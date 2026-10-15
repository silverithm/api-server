package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabTransportDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabGuideDelivery;
import com.silverithm.vehicleplacementsystem.lab.entity.LabTransportLog;
import com.silverithm.vehicleplacementsystem.lab.entity.LabTransportVehicle;
import com.silverithm.vehicleplacementsystem.lab.repository.LabGuideDeliveryRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabTransportLogRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabTransportVehicleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 송영 — 운행일지(고시 제34조 제4항)·차량과 보험·수칙/운행표 제공 기록.
 *
 * <p>운행일지는 날짜 x 등원/하원 x 차량 1건이라 PUT이 upsert한다. 운전원·동승자·탑승 어르신 id는 클라이언트가
 * 보내는 값이라 모두 요청자 기관 소속인지 확인하고 아니면 "없음"과 같은 404로 답한다. 운전원과 동승자는 다른
 * 사람이어야 하고(한 사람이 두 역할을 겸하면 동승자가 없는 것이다), 도착 시각은 출발 시각 이상이다.
 * 차량번호는 기관 안에서 하나(409)다. 제공 기록은 어르신 여러 명을 한 번에 받아 한 명당 한 건을 만든다.
 */
@Service
public class LabTransportService {

    static final Set<String> DIRECTIONS = Set.of("PICKUP", "DROPOFF");
    static final Set<String> DELIVERY_KINDS = Set.of("TRANSPORT_RULES", "TIMETABLE");
    static final Set<String> DELIVERY_METHODS = Set.of("FACE", "PAPER", "SMS", "SNS", "OTHER");
    static final int MAX_RIDERS = 30;
    static final int MAX_BATCH = 100;

    private final LabAccessService access;
    private final LabStaffService staff;
    private final LabElderDirectory elders;
    private final LabTransportLogRepository logs;
    private final LabTransportVehicleRepository vehicles;
    private final LabGuideDeliveryRepository deliveries;
    private final TransactionTemplate tx;

    public LabTransportService(LabAccessService access, LabStaffService staff, LabElderDirectory elders,
                               LabTransportLogRepository logs, LabTransportVehicleRepository vehicles,
                               LabGuideDeliveryRepository deliveries, PlatformTransactionManager txManager) {
        this.access = access;
        this.staff = staff;
        this.elders = elders;
        this.logs = logs;
        this.vehicles = vehicles;
        this.deliveries = deliveries;
        this.tx = new TransactionTemplate(txManager);
    }

    /** JSON 열에 저장하는 탑승자 모양 — 시각은 'HH:mm' 문자열 */
    record StoredRider(Long elderlyId, boolean boarded, String boardTime, String alightTime, String note) {
    }

    // ---- 운행일지 ----

    /** date 또는 month 중 하나 — 둘 다 주면 date를 쓴다. run_date·등원 먼저·차량번호 순 */
    @Transactional(readOnly = true)
    public List<LabTransportDto.LogView> listLogs(String date, String month) {
        Long companyId = access.requireCompanyId();
        boolean hasDate = date != null && !date.isBlank();
        boolean hasMonth = month != null && !month.isBlank();
        if (!hasDate && !hasMonth) {
            throw LabValidation.bad("date 또는 month 중 하나를 보내 주세요");
        }
        LocalDate from;
        LocalDate to;
        if (hasDate) {
            from = LabValidation.date(date, "날짜");
            to = from;
        } else {
            YearMonth ym = LabValidation.yearMonth(month.strip(), "월");
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        }
        List<LabTransportLog> rows = new ArrayList<>(
                logs.findByCompanyIdAndRunDateBetweenOrderByRunDateAscVehicleNoAscIdAsc(companyId, from, to));
        rows.sort(Comparator.comparing(LabTransportLog::getRunDate)
                .thenComparing(r -> "PICKUP".equals(r.getDirection()) ? 0 : 1)
                .thenComparing(LabTransportLog::getVehicleNo).thenComparing(LabTransportLog::getId));
        Names names = names(rows, companyId);
        return rows.stream().map(r -> logView(r, names)).toList();
    }

    public LabTransportDto.LogView upsertLog(LabTransportDto.LogInput input) {
        LabAccessService.Caller caller = access.requireCaller();
        Long companyId = caller.companyId();
        if (input == null) {
            throw LabValidation.bad("운행일지 내용을 보내 주세요");
        }
        LocalDate runDate = LabValidation.pastOrToday(input.runDate(), "운행일");
        String direction = LabValidation.oneOf(input.direction(), DIRECTIONS, "등원/하원");
        String vehicleNo = LabValidation.requiredText(input.vehicleNo(), 20, "차량번호");
        Long driverId = LabValidation.required(input.driverMemberId(), "운전원");
        Long escortId = input.escortMemberId();
        if (escortId != null && escortId.equals(driverId)) {
            throw LabValidation.bad("운전원과 동승자는 다른 사람이어야 합니다");
        }
        LocalTime depart = LabValidation.clockOrNull(input.departTime(), "출발 시각");
        LocalTime arrive = LabValidation.clockOrNull(input.arriveTime(), "도착 시각");
        if (depart != null && arrive != null && arrive.isBefore(depart)) {
            throw LabValidation.bad("도착 시각은 출발 시각보다 빠를 수 없습니다");
        }
        String note = LabValidation.text(input.note(), 300, "특이사항");
        List<StoredRider> riders = riders(input.riders());

        // 입력 모양이 맞은 뒤에 기관 소속을 확인한다 — 하나라도 다른 기관이면 통째로 404
        Set<Long> staffIds = new LinkedHashSet<>();
        staffIds.add(driverId);
        if (escortId != null) {
            staffIds.add(escortId);
        }
        staff.requireAllInCompany(staffIds, companyId);
        elders.requireAllInCompany(riders.stream().map(StoredRider::elderlyId).toList(), companyId);

        String ridersJson = LabJson.write(riders);
        String writer = LabValidation.writerName(caller);
        LabTransportLog saved = LabValidation.upsertWithRetry(tx, () -> {
            LabTransportLog row = logs
                    .findByCompanyIdAndRunDateAndDirectionAndVehicleNo(companyId, runDate, direction, vehicleNo)
                    .orElseGet(() -> new LabTransportLog(companyId, runDate, direction, vehicleNo));
            row.fill(driverId, escortId, depart, arrive, ridersJson, note, writer);
            return logs.saveAndFlush(row);
        });
        return logView(saved, names(List.of(saved), companyId));
    }

    @Transactional
    public void deleteLog(Long id) {
        Long companyId = access.requireCompanyId();
        logs.delete(logs.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("운행일지를 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    private static List<StoredRider> riders(List<LabTransportDto.RiderInput> input) {
        if (input == null || input.isEmpty()) {
            throw LabValidation.bad("탑승 어르신을 1명 이상 입력해 주세요");
        }
        if (input.size() > MAX_RIDERS) {
            throw LabValidation.bad("탑승 어르신은 한 일지에 최대 " + MAX_RIDERS + "명까지 입력할 수 있습니다");
        }
        Set<Long> seen = new HashSet<>();
        List<StoredRider> result = new ArrayList<>();
        for (LabTransportDto.RiderInput r : input) {
            if (r == null || r.elderlyId() == null) {
                throw LabValidation.bad("탑승 어르신을 선택해 주세요");
            }
            if (!seen.add(r.elderlyId())) {
                throw LabValidation.bad("같은 어르신이 탑승자에 두 번 들어 있습니다");
            }
            LocalTime board = LabValidation.clockOrNull(r.boardTime(), "탑승 시각");
            LocalTime alight = LabValidation.clockOrNull(r.alightTime(), "하차 시각");
            result.add(new StoredRider(r.elderlyId(), Boolean.TRUE.equals(r.boarded()), LabValidation.formatClock(board),
                    LabValidation.formatClock(alight), LabValidation.text(r.note(), 100, "탑승자 메모")));
        }
        return result;
    }

    /** 일지들의 운전원·동승자·탑승 어르신 이름 — 직원·어르신 각각 한 번에 읽는다 */
    private Names names(List<LabTransportLog> rows, Long companyId) {
        Set<Long> staffIds = new LinkedHashSet<>();
        for (LabTransportLog r : rows) {
            staffIds.add(r.getDriverMemberId());
            if (r.getEscortMemberId() != null) {
                staffIds.add(r.getEscortMemberId());
            }
        }
        return new Names(staff.namesOf(staffIds, companyId),
                rows.isEmpty() ? Map.of() : elders.namesInCompany(companyId));
    }

    private record Names(Map<Long, String> staff, Map<Long, String> elders) {
    }

    private static LabTransportDto.LogView logView(LabTransportLog r, Names names) {
        List<LabTransportDto.Rider> riders = LabJson.readList(r.getRiders(), StoredRider.class).stream()
                .map(x -> new LabTransportDto.Rider(x.elderlyId(), names.elders().getOrDefault(x.elderlyId(), ""),
                        x.boarded(), x.boardTime(), x.alightTime(), x.note())).toList();
        Long escort = r.getEscortMemberId();
        return new LabTransportDto.LogView(r.getId(), r.getRunDate(), r.getDirection(), r.getVehicleNo(),
                r.getDriverMemberId(), names.staff().getOrDefault(r.getDriverMemberId(), ""), escort,
                escort == null ? null : names.staff().getOrDefault(escort, ""),
                LabValidation.formatClock(r.getDepartTime()), LabValidation.formatClock(r.getArriveTime()), riders,
                r.getNote(), r.getWriterName(), r.getUpdatedAt());
    }

    // ---- 차량·보험 ----

    @Transactional(readOnly = true)
    public List<LabTransportDto.VehicleView> listVehicles() {
        Long companyId = access.requireCompanyId();
        return vehicles.findByCompanyIdOrderByActiveDescVehicleNoAscIdAsc(companyId).stream()
                .map(LabTransportService::vehicleView).toList();
    }

    @Transactional
    public LabTransportDto.VehicleView createVehicle(LabTransportDto.VehicleInput input) {
        Long companyId = access.requireCompanyId();
        LabTransportVehicle vehicle = new LabTransportVehicle(companyId);
        applyVehicle(vehicle, input, companyId);
        return vehicleView(saveVehicle(vehicle));
    }

    @Transactional
    public LabTransportDto.VehicleView updateVehicle(Long id, LabTransportDto.VehicleInput input) {
        Long companyId = access.requireCompanyId();
        LabTransportVehicle vehicle = vehicles.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabTransportService::vehicleNotFound);
        applyVehicle(vehicle, input, companyId);
        return vehicleView(saveVehicle(vehicle));
    }

    @Transactional
    public void deleteVehicle(Long id) {
        Long companyId = access.requireCompanyId();
        vehicles.delete(vehicles.findByIdAndCompanyId(id, companyId).orElseThrow(LabTransportService::vehicleNotFound));
    }

    private void applyVehicle(LabTransportVehicle vehicle, LabTransportDto.VehicleInput input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("차량 내용을 보내 주세요");
        }
        String vehicleNo = LabValidation.requiredText(input.vehicleNo(), 20, "차량번호");
        String label = LabValidation.text(input.label(), 50, "차종");
        String insurer = LabValidation.text(input.insurer(), 50, "보험사");
        String policyNo = LabValidation.text(input.policyNo(), 40, "증권번호");
        LocalDate from = input.insuranceFrom() == null ? null
                : LabValidation.plausibleDate(input.insuranceFrom(), "보험 시작일");
        LocalDate to = input.insuranceTo() == null ? null
                : LabValidation.plausibleDate(input.insuranceTo(), "보험 만기일");
        if (from != null && to != null && to.isBefore(from)) {
            throw LabValidation.bad("보험 만기일은 시작일보다 앞설 수 없습니다");
        }
        String note = LabValidation.text(input.note(), 200, "메모");
        boolean comprehensive = Boolean.TRUE.equals(input.comprehensive());
        // 활성 여부를 안 보내면 쓰는 차량으로 본다
        boolean active = input.active() == null || input.active();
        vehicles.findByCompanyIdAndVehicleNo(companyId, vehicleNo).ifPresent(existing -> {
            if (!existing.getId().equals(vehicle.getId())) {
                throw duplicateVehicle();
            }
        });
        vehicle.fill(vehicleNo, label, insurer, policyNo, from, to, comprehensive, active, note);
    }

    /** 사전 확인을 빠져나간 동시 요청은 유일 키가 막으므로 같은 409로 바꾼다 */
    private LabTransportVehicle saveVehicle(LabTransportVehicle vehicle) {
        try {
            return vehicles.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException e) {
            throw duplicateVehicle();
        }
    }

    private static CustomException duplicateVehicle() {
        return new CustomException("이미 등록된 차량번호입니다", HttpStatus.CONFLICT);
    }

    private static CustomException vehicleNotFound() {
        return new CustomException("차량을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }

    private static LabTransportDto.VehicleView vehicleView(LabTransportVehicle v) {
        return new LabTransportDto.VehicleView(v.getId(), v.getVehicleNo(), v.getLabel(), v.getInsurer(),
                v.getPolicyNo(), v.getInsuranceFrom(), v.getInsuranceTo(), v.isComprehensive(), v.isActive(),
                v.getNote(), v.getUpdatedAt());
    }

    // ---- 수칙·운행표 제공 ----

    /** kind는 필수 — 제공일 최신순. elderlyId를 주면 그 어르신만 */
    @Transactional(readOnly = true)
    public List<LabTransportDto.DeliveryView> listDeliveries(String kind, Long elderlyId) {
        Long companyId = access.requireCompanyId();
        if (kind == null || kind.isBlank()) {
            throw LabValidation.bad("kind를 보내 주세요");
        }
        String k = LabValidation.oneOf(kind, DELIVERY_KINDS, "제공 종류");
        List<LabGuideDelivery> rows;
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
            rows = deliveries.findByCompanyIdAndKindAndElderlyIdOrderByDeliveredOnDescIdDesc(companyId, k, elderlyId);
        } else {
            rows = deliveries.findByCompanyIdAndKindOrderByDeliveredOnDescIdDesc(companyId, k);
        }
        Map<Long, String> names = rows.isEmpty() ? Map.of() : elders.namesInCompany(companyId);
        return rows.stream().map(r -> deliveryView(r, names.getOrDefault(r.getElderlyId(), ""))).toList();
    }

    /** 어르신마다 한 건씩 한 트랜잭션으로 만든다 — 한 명이라도 다른 기관이면 하나도 만들지 않고 404 */
    @Transactional
    public List<LabTransportDto.DeliveryView> createDeliveries(LabTransportDto.DeliveryBatchInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("제공 기록을 보내 주세요");
        }
        String kind = LabValidation.oneOf(input.kind(), DELIVERY_KINDS, "제공 종류");
        LocalDate deliveredOn = LabValidation.pastOrToday(input.deliveredOn(), "제공일");
        String method = LabValidation.oneOf(input.method(), DELIVERY_METHODS, "제공 방법");
        String note = LabValidation.text(input.note(), 200, "메모");
        // 같은 어르신이 두 번 들어 있어도 한 건만 만든다(화면의 이중 선택이 같은 기록을 겹치게 하지 않도록)
        List<Long> ids = LabValidation.idList(input.elderlyIds(), MAX_BATCH, "어르신");
        if (ids.isEmpty()) {
            throw LabValidation.bad("제공한 어르신을 1명 이상 선택해 주세요");
        }
        Map<Long, String> names = elders.requireAllInCompany(ids, companyId);
        List<LabTransportDto.DeliveryView> result = new ArrayList<>();
        for (Long id : ids) {
            LabGuideDelivery saved = deliveries.saveAndFlush(
                    new LabGuideDelivery(companyId, id, kind, deliveredOn, method, note));
            result.add(deliveryView(saved, names.get(id)));
        }
        return result;
    }

    @Transactional
    public void deleteDelivery(Long id) {
        Long companyId = access.requireCompanyId();
        deliveries.delete(deliveries.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("제공 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    private static LabTransportDto.DeliveryView deliveryView(LabGuideDelivery d, String elderlyName) {
        return new LabTransportDto.DeliveryView(d.getId(), d.getElderlyId(), elderlyName, d.getKind(),
                d.getDeliveredOn(), d.getMethod(), d.getNote(), d.getUpdatedAt());
    }
}
