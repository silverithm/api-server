package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicationDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicationDose;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicationRequest;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicationDoseRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicationRequestRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 투약의뢰서와 투약 기록.
 *
 * <p>의뢰서는 보호자가 맡긴 약 목록(1~15개, 약마다 먹는 시간대 1개 이상)과 기간을 담는다. 투약 기록은 의뢰서 x 날짜 x
 * 시간대 1건이고, 같은 칸을 다시 저장하면 덮어쓴다(누른 사람이 바뀌거나 상태를 고친 경우). 기록은 의뢰 기간 안·의뢰서에 있는
 * 시간대·오늘까지만 받는다. 끝난(ENDED) 의뢰서라도 기간 안 날짜는 기록할 수 있다 — 며칠 늦게 적는 일이 흔하기 때문이다.
 * 투약 기록이 하나라도 있는 의뢰서는 지울 수 없고(감사 때 "누가 언제 먹였나"가 사라지므로) 끝내기로 바꾼다.
 */
@Service
public class LabMedicationService {

    static final int MAX_MEDICINES = 15;
    private static final int NAME = 50;
    private static final int MEDICINE_NAME = 60;
    private static final int DOSE = 40;
    private static final int ITEM_NOTE = 100;
    private static final int NOTE = 500;
    static final List<String> SLOT_ORDER = List.of("MORNING", "LUNCH", "EVENING", "BEDTIME");
    private static final Set<String> SLOTS = Set.copyOf(SLOT_ORDER);
    private static final Set<String> RELATIONS = Set.of("SELF", "SPOUSE", "CHILD", "OTHER_FAMILY", "OTHER");
    private static final Set<String> ROUTES = Set.of("ORAL", "TOPICAL", "EYE", "INHALE", "OTHER");
    private static final Set<String> TIMINGS = Set.of("BEFORE_MEAL", "AFTER_MEAL", "WITH_MEAL", "ANYTIME");
    private static final Set<String> REQUEST_STATUSES = Set.of(LabMedicationRequest.ACTIVE, LabMedicationRequest.ENDED);
    private static final Set<String> DOSE_STATUSES = Set.of(LabMedicationDose.GIVEN, LabMedicationDose.SKIPPED,
            LabMedicationDose.REFUSED);

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabMedicationRequestRepository requests;
    private final LabMedicationDoseRepository doses;
    private final TransactionTemplate tx;

    public LabMedicationService(LabAccessService access, LabElderDirectory elders,
                                LabMedicationRequestRepository requests, LabMedicationDoseRepository doses,
                                PlatformTransactionManager txManager) {
        this.access = access;
        this.elders = elders;
        this.requests = requests;
        this.doses = doses;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---- 투약의뢰서 ----

    /** status / elderlyId로 좁힌다(둘 다 없으면 전부). 시작일 최신순 */
    @Transactional(readOnly = true)
    public List<LabMedicationDto.RequestView> listRequests(String status, Long elderlyId) {
        Long companyId = access.requireCompanyId();
        String wanted = status == null || status.isBlank() ? null
                : LabValidation.oneOf(status, REQUEST_STATUSES, "상태");
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
        }
        List<LabMedicationRequest> rows;
        if (elderlyId != null && wanted != null) {
            rows = requests.findByCompanyIdAndElderlyIdAndStatusOrderByStartDateDescIdDesc(companyId, elderlyId, wanted);
        } else if (elderlyId != null) {
            rows = requests.findByCompanyIdAndElderlyIdOrderByStartDateDescIdDesc(companyId, elderlyId);
        } else if (wanted != null) {
            rows = requests.findByCompanyIdAndStatusOrderByStartDateDescIdDesc(companyId, wanted);
        } else {
            rows = requests.findByCompanyIdOrderByStartDateDescIdDesc(companyId);
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream().map(r -> toView(r, names.getOrDefault(r.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabMedicationDto.RequestView createRequest(LabMedicationDto.RequestInput input) {
        Long companyId = access.requireCompanyId();
        LabMedicationRequest request = new LabMedicationRequest(companyId);
        applyRequest(request, input, companyId);
        LabMedicationRequest saved = requests.saveAndFlush(request);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public LabMedicationDto.RequestView updateRequest(Long id, LabMedicationDto.RequestInput input) {
        Long companyId = access.requireCompanyId();
        LabMedicationRequest request = requests.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabMedicationService::requestNotFound);
        applyRequest(request, input, companyId);
        LabMedicationRequest saved = requests.saveAndFlush(request);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    /** 투약 기록이 있으면 409 — 지우지 말고 끝내기(ENDED)로 바꾸게 한다 */
    @Transactional
    public void deleteRequest(Long id) {
        Long companyId = access.requireCompanyId();
        LabMedicationRequest request = requests.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabMedicationService::requestNotFound);
        if (doses.existsByRequestId(request.getId())) {
            throw new CustomException("투약 기록이 있는 의뢰서는 삭제할 수 없습니다. 끝내기로 바꿔 주세요",
                    HttpStatus.CONFLICT);
        }
        requests.delete(request);
    }

    private void applyRequest(LabMedicationRequest request, LabMedicationDto.RequestInput input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("투약의뢰서 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        LocalDate requestedOn = LabValidation.plausibleDate(input.requestedOn(), "의뢰일");
        String requester = LabValidation.requiredText(input.requesterName(), NAME, "의뢰자 이름");
        String relation = LabValidation.oneOf(input.requesterRelation(), RELATIONS, "의뢰자 관계");
        List<LabMedicationDto.MedicationItem> medicines = medicines(input.medicines());
        LocalDate start = LabValidation.plausibleDate(input.startDate(), "투약 시작일");
        LocalDate end = input.endDate() == null ? null : LabValidation.plausibleDate(input.endDate(), "투약 끝나는 날");
        if (end != null && end.isBefore(start)) {
            throw LabValidation.bad("투약 끝나는 날이 시작일보다 앞일 수 없습니다");
        }
        String status = input.status() == null || input.status().isBlank()
                ? LabMedicationRequest.ACTIVE : LabValidation.oneOf(input.status(), REQUEST_STATUSES, "상태");
        String note = LabValidation.text(input.note(), NOTE, "메모");
        String writer = LabValidation.text(input.writerName(), NAME, "작성자 이름");

        // 이미 투약을 기록한 의뢰서를 고칠 때 기록이 새 기간·시간대·어르신 밖으로 밀려나면 안 된다
        if (request.getId() != null) {
            List<LabMedicationDose> recorded = doses.findByRequestId(request.getId());
            if (!recorded.isEmpty() && !request.getElderlyId().equals(input.elderlyId())) {
                throw new CustomException("투약 기록이 있는 의뢰서는 어르신을 바꿀 수 없습니다", HttpStatus.CONFLICT);
            }
            Set<String> slots = slotsOf(medicines);
            for (LabMedicationDose dose : recorded) {
                boolean outside = dose.getGivenOn().isBefore(start) || (end != null && dose.getGivenOn().isAfter(end));
                if (outside || !slots.contains(dose.getSlot())) {
                    throw new CustomException("이미 기록한 투약(" + dose.getGivenOn()
                            + ")이 바꾼 기간·시간대 밖에 있습니다. 기록을 먼저 확인해 주세요", HttpStatus.CONFLICT);
                }
            }
        }
        request.fill(input.elderlyId(), requestedOn, requester, relation, LabJson.write(medicines), start, end,
                Boolean.TRUE.equals(input.prescriptionChecked()), status, note, writer,
                Boolean.TRUE.equals(input.aiAssisted()));
    }

    /** 약 목록 검증 — 1~15개, 약마다 이름·경로·시간 허용 값과 먹는 시간대 1개 이상. 시간대는 중복을 없애고 아침→자기 전 순으로 정렬 */
    static List<LabMedicationDto.MedicationItem> medicines(List<LabMedicationDto.MedicationItem> input) {
        if (input == null || input.isEmpty()) {
            throw LabValidation.bad("약을 한 개 이상 적어 주세요");
        }
        if (input.size() > MAX_MEDICINES) {
            throw LabValidation.bad("약은 최대 " + MAX_MEDICINES + "개까지 적을 수 있습니다");
        }
        List<LabMedicationDto.MedicationItem> result = new ArrayList<>(input.size());
        for (LabMedicationDto.MedicationItem item : input) {
            if (item == null) {
                throw LabValidation.bad("약 목록에 올바르지 않은 값이 있습니다");
            }
            String name = LabValidation.requiredText(item.name(), MEDICINE_NAME, "약 이름");
            String dose = LabValidation.text(item.dose(), DOSE, "용량");
            String route = LabValidation.oneOf(item.route(), ROUTES, "투여 경로");
            String timing = LabValidation.oneOf(item.timing(), TIMINGS, "복용 시점");
            if (item.slots() == null || item.slots().isEmpty()) {
                throw LabValidation.bad("'" + name + "'의 먹는 시간(아침·점심·저녁·자기 전)을 한 개 이상 골라 주세요");
            }
            Set<String> chosen = new HashSet<>();
            for (String slot : item.slots()) {
                chosen.add(LabValidation.oneOf(slot, SLOTS, "먹는 시간"));
            }
            List<String> slots = SLOT_ORDER.stream().filter(chosen::contains).toList();
            result.add(new LabMedicationDto.MedicationItem(name, dose, route, slots, timing,
                    LabValidation.text(item.note(), ITEM_NOTE, "약 메모")));
        }
        return result;
    }

    private static Set<String> slotsOf(List<LabMedicationDto.MedicationItem> medicines) {
        Set<String> slots = new HashSet<>();
        for (LabMedicationDto.MedicationItem item : medicines) {
            slots.addAll(item.slots());
        }
        return slots;
    }

    // ---- 투약 기록 ----

    /**
     * date를 주면 그날 기관 전체, 아니면 elderlyId + month(그 어르신의 한 달)가 필요하다. 날짜순.
     */
    @Transactional(readOnly = true)
    public List<LabMedicationDto.DoseView> listDoses(String date, Long elderlyId, String month) {
        Long companyId = access.requireCompanyId();
        List<LabMedicationDose> rows;
        if (date != null) {
            LocalDate day = LabValidation.plausibleDate(LabValidation.date(date, "날짜"), "날짜");
            if (elderlyId != null) {
                elders.requireInCompany(elderlyId, companyId);
            }
            rows = doses.findByCompanyIdAndGivenOnOrderByElderlyIdAscIdAsc(companyId, day).stream()
                    .filter(d -> elderlyId == null || d.getElderlyId().equals(elderlyId)).toList();
        } else {
            if (elderlyId == null || month == null) {
                throw LabValidation.bad("날짜(YYYY-MM-DD) 또는 어르신과 월(YYYY-MM)을 지정해 주세요");
            }
            YearMonth ym = LabValidation.yearMonth(month, "월");
            elders.requireInCompany(elderlyId, companyId);
            rows = doses.findByCompanyIdAndElderlyIdAndGivenOnBetweenOrderByGivenOnAscIdAsc(companyId, elderlyId,
                    ym.atDay(1), ym.atEndOfMonth());
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream()
                .sorted(Comparator.comparing(LabMedicationDose::getGivenOn)
                        .thenComparing(LabMedicationDose::getElderlyId)
                        .thenComparingInt(d -> SLOT_ORDER.indexOf(d.getSlot()))
                        .thenComparing(LabMedicationDose::getId))
                .map(d -> toView(d, names.getOrDefault(d.getElderlyId(), "")))
                .toList();
    }

    /** requestId + givenOn + slot로 덮어쓴다. 의뢰 기간·시간대·미래 날짜·GIVEN 시각을 검증한다 */
    public LabMedicationDto.DoseView upsertDose(LabMedicationDto.DoseInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("투약 기록 내용을 보내 주세요");
        }
        Long requestId = LabValidation.required(input.requestId(), "투약의뢰서");
        LocalDate day = LabValidation.plausibleDate(input.givenOn(), "투약 날짜");
        if (day.isAfter(LabValidation.seoulToday())) {
            throw LabValidation.bad("투약 날짜가 오늘보다 뒤일 수 없습니다");
        }
        String slot = LabValidation.oneOf(input.slot(), SLOTS, "먹는 시간");
        String status = LabValidation.oneOf(input.status(), DOSE_STATUSES, "투약 상태");
        String giver = LabValidation.requiredText(input.giverName(), NAME, "투약한 사람 이름");
        // 시각은 실제로 먹인(GIVEN) 때만 뜻이 있다 — 안 먹인 기록에 시각이 남으면 감사 때 혼동된다
        String givenAt = LabMedicationDose.GIVEN.equals(status) ? LabValidation.clock(input.givenAt(), "투약 시각") : null;
        String observation = LabValidation.text(input.observation(), NOTE, "투약 후 상태");

        LabMedicationDose saved = LabValidation.upsertWithRetry(tx, () -> {
            LabMedicationRequest request = requests.findByIdAndCompanyId(requestId, companyId)
                    .orElseThrow(LabMedicationService::requestNotFound);
            if (input.elderlyId() != null && !input.elderlyId().equals(request.getElderlyId())) {
                throw LabValidation.bad("투약의뢰서의 어르신과 다릅니다");
            }
            if (day.isBefore(request.getStartDate())
                    || (request.getEndDate() != null && day.isAfter(request.getEndDate()))) {
                throw LabValidation.bad("투약 날짜가 의뢰 기간("
                        + (request.getEndDate() == null ? request.getStartDate() + "부터"
                        : request.getStartDate() + " ~ " + request.getEndDate()) + ")을 벗어났습니다");
            }
            if (!slotsOf(LabJson.readMedicationItems(request.getMedicines())).contains(slot)) {
                throw LabValidation.bad("이 의뢰서에 없는 시간대입니다");
            }
            LabMedicationDose dose = doses.findByRequestIdAndGivenOnAndSlot(requestId, day, slot)
                    .orElseGet(() -> new LabMedicationDose(companyId, request.getElderlyId(), requestId, day, slot));
            dose.fill(status, givenAt, giver, observation);
            return doses.saveAndFlush(dose);
        });
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void deleteDose(Long id) {
        Long companyId = access.requireCompanyId();
        doses.delete(doses.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("투약 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    private static LabMedicationDto.RequestView toView(LabMedicationRequest r, String elderlyName) {
        return new LabMedicationDto.RequestView(r.getId(), r.getElderlyId(), elderlyName, r.getRequestedOn(),
                r.getRequesterName(), r.getRequesterRelation(), LabJson.readMedicationItems(r.getMedicines()),
                r.getStartDate(), r.getEndDate(), r.isPrescriptionChecked(), r.getStatus(), r.getNote(),
                r.getWriterName(), r.isAiAssisted(), r.getUpdatedAt());
    }

    private static LabMedicationDto.DoseView toView(LabMedicationDose d, String elderlyName) {
        return new LabMedicationDto.DoseView(d.getId(), d.getElderlyId(), elderlyName, d.getRequestId(),
                d.getGivenOn(), d.getSlot(), d.getStatus(), d.getGivenAt(), d.getGiverName(), d.getObservation(),
                d.getUpdatedAt());
    }

    private static CustomException requestNotFound() {
        return new CustomException("투약의뢰서를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
