package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabFacilityDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabFacilityLog;
import com.silverithm.vehicleplacementsystem.lab.entity.LabFacilityProfile;
import com.silverithm.vehicleplacementsystem.lab.repository.LabFacilityLogRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabFacilityProfileRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시설 관리 대장 — 시설 정보(기관당 한 줄)와 점검·청소·검사·보험 실시 기록.
 *
 * <p>항목 코드는 19개 허용 목록만 받는다(웹과 같은 목록). 소방 보고일은 소방 두 항목에서만, 보험 만기는 보험 두 항목에서만
 * 저장하고 다른 항목이면 조용히 비운다. 다음 기한·보관 만료·적용 판정은 웹이 계산하며, 서버는 값의 모양과 기관 범위만 지킨다.
 * 실시일은 미래일 수 없다. 다른 기관의 기록 id는 "없음"과 같은 404로 답한다.
 */
@Service
public class LabFacilityService {

    /** 시설 관리 대장 항목 코드 19개 — 웹 FACILITY_ITEM_CODES와 같은 순서·목록 */
    static final List<String> FACILITY_ITEM_CODES = List.of("FIRE_OPERATION", "FIRE_COMPREHENSIVE",
            "FIRE_DRILL_LEGAL", "FIRE_MANAGER_TRAINING", "ELEC_GENERAL", "ELEC_SELF_USE", "GAS_CITY_REGULATOR",
            "GAS_CITY_SPECIAL", "GAS_LPG_INSPECT", "ELEV_SELF_MONTHLY", "ELEV_ANNUAL", "TANK_CLEAN", "TANK_HYGIENE",
            "TANK_WATER_QUALITY", "SEPTIC_CLEAN", "DISINFECT_QUARTERLY", "SOCIAL_SAFETY_HALF",
            "INSURANCE_FACILITY", "INSURANCE_PROFESSIONAL");
    private static final Set<String> ITEM_CODES = Set.copyOf(FACILITY_ITEM_CODES);
    /** 소방 자체점검 결과 보고일을 받는 항목 */
    private static final Set<String> REPORT_ITEMS = Set.of("FIRE_OPERATION", "FIRE_COMPREHENSIVE");
    /** 만기가 있는 보험 항목 */
    private static final Set<String> INSURANCE_ITEMS = Set.of("INSURANCE_FACILITY", "INSURANCE_PROFESSIONAL");

    static final Set<String> SPRINKLERS = Set.of("NONE", "SIMPLE", "FULL", "UNKNOWN");
    static final Set<String> POWER_KINDS = Set.of("GENERAL", "SELF_USE", "UNKNOWN");
    static final Set<String> GAS_KINDS = Set.of("NONE", "CITY", "LPG", "UNKNOWN");
    static final Set<String> ELEVATORS = Set.of("NONE", "CENTER", "BUILDING", "UNKNOWN");
    static final Set<String> WATER_TANKS = Set.of("NONE", "LARGE", "SMALL", "UNKNOWN");
    static final Set<String> SEPTICS = Set.of("NONE", "YES", "UNKNOWN");
    static final Set<String> OVERRIDES = Set.of("YES", "NO");
    static final Set<String> DONE_BY = Set.of("SELF", "VENDOR", "AGENCY");
    static final Set<String> RESULTS = Set.of("PASS", "FIX_NEEDED", "FAIL");

    private static final String UNKNOWN = "UNKNOWN";
    private static final int MIN_AREA = 1;
    private static final int MAX_AREA = 1_000_000;
    private static final int MAX_LPG_KG = 100_000;
    /** 오래된 건물도 있어 실험실 공통 2000년 하한 대신 1900년부터 받는다 */
    private static final int MIN_APPROVAL_YEAR = 1900;

    private final LabAccessService access;
    private final LabFacilityProfileRepository profiles;
    private final LabFacilityLogRepository logs;
    private final TransactionTemplate tx;

    public LabFacilityService(LabAccessService access, LabFacilityProfileRepository profiles,
                              LabFacilityLogRepository logs, PlatformTransactionManager txManager) {
        this.access = access;
        this.profiles = profiles;
        this.logs = logs;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---- 시설 정보 ----

    /** 저장한 적이 없으면 id가 null이고 모든 칸이 기본값(UNKNOWN)인 정보를 돌려준다 */
    @Transactional(readOnly = true)
    public LabFacilityDto.ProfileView getProfile() {
        Long companyId = access.requireCompanyId();
        return profiles.findByCompanyId(companyId).map(LabFacilityService::view).orElseGet(LabFacilityService::emptyView);
    }

    /** 기관당 한 줄을 만들거나 고친다 — 항상 통째로 덮어쓴다 */
    public LabFacilityDto.ProfileView saveProfile(LabFacilityDto.ProfileInput input) {
        LabAccessService.Caller caller = access.requireCaller();
        Long companyId = caller.companyId();
        if (input == null) {
            throw LabValidation.bad("시설 정보를 보내 주세요");
        }
        LocalDate approvedOn = approvalDate(input.approvedOn());
        Integer area = input.grossAreaM2() == null ? null
                : (int) LabValidation.longInRange(input.grossAreaM2().longValue(), MIN_AREA, MAX_AREA, "연면적(㎡)");
        String sprinkler = enumOrUnknown(input.sprinkler(), SPRINKLERS, "스프링클러");
        String powerKind = enumOrUnknown(input.powerKind(), POWER_KINDS, "전기 설비");
        String gasKind = enumOrUnknown(input.gasKind(), GAS_KINDS, "가스");
        String elevator = enumOrUnknown(input.elevator(), ELEVATORS, "승강기");
        String waterTank = enumOrUnknown(input.waterTank(), WATER_TANKS, "저수조");
        String septic = enumOrUnknown(input.septic(), SEPTICS, "정화조");
        // LPG가 아니면 용량을 비운다(받은 값이 무엇이든 조용히)
        Integer lpgKg = !"LPG".equals(gasKind) || input.lpgCapacityKg() == null ? null
                : (int) LabValidation.longInRange(input.lpgCapacityKg().longValue(), 0, MAX_LPG_KG, "LPG 저장 용량(kg)");
        Map<String, String> overrides = overrides(input.appliesOverride());
        String fireManager = LabValidation.text(input.fireManagerName(), 50, "소방안전관리자 이름");
        String note = LabValidation.text(input.note(), 500, "메모");
        String writer = access.displayName(caller);
        String overridesJson = LabJson.write(overrides);

        LabFacilityProfile saved = LabValidation.upsertWithRetry(tx, () -> {
            LabFacilityProfile row = profiles.findByCompanyId(companyId)
                    .orElseGet(() -> new LabFacilityProfile(companyId));
            row.fill(approvedOn, area, sprinkler, powerKind, gasKind, lpgKg, elevator, waterTank, septic,
                    overridesJson, fireManager, note, writer);
            return profiles.saveAndFlush(row);
        });
        return view(saved);
    }

    // ---- 실시 기록 ----

    /** year 또는 itemCode 중 하나는 꼭 필요하다 — 둘 다 주면 그 항목의 그 해. 실시일 최신순 */
    @Transactional(readOnly = true)
    public List<LabFacilityDto.LogView> listLogs(String year, String itemCode) {
        Long companyId = access.requireCompanyId();
        boolean hasYear = year != null && !year.isBlank();
        boolean hasItem = itemCode != null && !itemCode.isBlank();
        if (!hasYear && !hasItem) {
            throw LabValidation.bad("year 또는 itemCode 중 하나를 보내 주세요");
        }
        String code = hasItem ? LabValidation.oneOf(itemCode, ITEM_CODES, "항목 코드") : null;
        List<LabFacilityLog> rows;
        if (hasYear) {
            int y = LabValidation.year(year.strip(), "연도");
            LocalDate from = LocalDate.of(y, 1, 1);
            LocalDate to = LocalDate.of(y, 12, 31);
            rows = code == null ? logs.findByCompanyIdAndDoneOnBetweenOrderByDoneOnDescIdDesc(companyId, from, to)
                    : logs.findByCompanyIdAndItemCodeAndDoneOnBetweenOrderByDoneOnDescIdDesc(companyId, code, from, to);
        } else {
            rows = logs.findByCompanyIdAndItemCodeOrderByDoneOnDescIdDesc(companyId, code);
        }
        return rows.stream().map(LabFacilityService::view).toList();
    }

    @Transactional
    public LabFacilityDto.LogView createLog(LabFacilityDto.LogInput input) {
        LabAccessService.Caller caller = access.requireCaller();
        LabFacilityLog row = new LabFacilityLog(caller.companyId());
        applyLog(row, input, access.displayName(caller));
        return view(logs.saveAndFlush(row));
    }

    @Transactional
    public LabFacilityDto.LogView updateLog(Long id, LabFacilityDto.LogInput input) {
        LabAccessService.Caller caller = access.requireCaller();
        LabFacilityLog row = logs.findByIdAndCompanyId(id, caller.companyId()).orElseThrow(LabFacilityService::notFound);
        applyLog(row, input, access.displayName(caller));
        return view(logs.saveAndFlush(row));
    }

    @Transactional
    public void deleteLog(Long id) {
        Long companyId = access.requireCompanyId();
        logs.delete(logs.findByIdAndCompanyId(id, companyId).orElseThrow(LabFacilityService::notFound));
    }

    private void applyLog(LabFacilityLog row, LabFacilityDto.LogInput input, String writer) {
        if (input == null) {
            throw LabValidation.bad("시설 점검 기록을 보내 주세요");
        }
        String code = LabValidation.oneOf(input.itemCode(), ITEM_CODES, "항목 코드");
        LocalDate doneOn = LabValidation.pastOrToday(input.doneOn(), "실시일");
        String doneBy = LabValidation.oneOf(input.doneBy(), DONE_BY, "실시자 구분");
        String vendor = LabValidation.text(input.vendorName(), 100, "업체 이름");
        String result = LabValidation.oneOf(input.result(), RESULTS, "결과");
        String actionNote = LabValidation.text(input.actionNote(), 500, "조치 내용");
        String docLocation = LabValidation.text(input.docLocation(), 200, "보관 위치");

        // 소방 보고일은 소방 두 항목에서만 — 그 밖의 항목이면 받은 값을 조용히 비운다
        LocalDate reportedOn = null;
        if (REPORT_ITEMS.contains(code) && input.reportedOn() != null) {
            reportedOn = LabValidation.pastOrToday(input.reportedOn(), "소방 결과 보고일");
            if (reportedOn.isBefore(doneOn)) {
                throw LabValidation.bad("소방 결과 보고일은 실시일보다 앞일 수 없습니다");
            }
        }
        // 보험 만기는 보험 두 항목에서만, 이때는 필수이고 실시일보다 뒤여야 한다
        LocalDate validUntil = null;
        if (INSURANCE_ITEMS.contains(code)) {
            validUntil = LabValidation.plausibleDate(LabValidation.required(input.validUntil(), "보험 만기일"), "보험 만기일");
            if (!validUntil.isAfter(doneOn)) {
                throw LabValidation.bad("보험 만기일은 가입(실시)일보다 뒤여야 합니다");
            }
        }
        row.fill(code, doneOn, doneBy, vendor, result, actionNote, reportedOn, validUntil, docLocation, writer);
    }

    // ---- 값 정리 ----

    /** 사용승인일 — 미래 불가. 오래된 건물이 있어 1900년부터 받는다 */
    private static LocalDate approvalDate(LocalDate value) {
        if (value == null) {
            return null;
        }
        if (value.getYear() < MIN_APPROVAL_YEAR) {
            throw LabValidation.bad("사용승인일은 " + MIN_APPROVAL_YEAR + "년 이후여야 합니다");
        }
        if (value.isAfter(LabValidation.seoulToday())) {
            throw LabValidation.bad("사용승인일은 오늘 이후일 수 없습니다");
        }
        return value;
    }

    private static String enumOrUnknown(String value, Set<String> allowed, String label) {
        return value == null || value.isBlank() ? UNKNOWN : LabValidation.oneOf(value, allowed, label);
    }

    /** {항목 코드: YES|NO} — 모르는 코드나 값이면 400 */
    private static Map<String, String> overrides(Map<String, String> in) {
        Map<String, String> out = new LinkedHashMap<>();
        if (in == null) {
            return out;
        }
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (e.getKey() == null || !ITEM_CODES.contains(e.getKey())) {
                throw LabValidation.bad("적용 여부를 지정한 항목 코드가 올바르지 않습니다");
            }
            if (e.getValue() == null || !OVERRIDES.contains(e.getValue())) {
                throw LabValidation.bad("적용 여부는 YES 또는 NO만 지정할 수 있습니다");
            }
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    private static LabFacilityDto.ProfileView emptyView() {
        return new LabFacilityDto.ProfileView(null, null, null, UNKNOWN, UNKNOWN, UNKNOWN, null, UNKNOWN, UNKNOWN,
                UNKNOWN, new LinkedHashMap<>(), null, null, null, null);
    }

    private static LabFacilityDto.ProfileView view(LabFacilityProfile p) {
        return new LabFacilityDto.ProfileView(p.getId(), p.getApprovedOn(), p.getGrossAreaM2(), p.getSprinkler(),
                p.getPowerKind(), p.getGasKind(), p.getLpgCapacityKg(), p.getElevator(), p.getWaterTank(),
                p.getSeptic(), LabJson.readStringMap(p.getAppliesOverride()), p.getFireManagerName(), p.getNote(),
                p.getWriterName(), p.getUpdatedAt());
    }

    private static LabFacilityDto.LogView view(LabFacilityLog l) {
        return new LabFacilityDto.LogView(l.getId(), l.getItemCode(), l.getDoneOn(), l.getDoneBy(), l.getVendorName(),
                l.getResult(), l.getActionNote(), l.getReportedOn(), l.getValidUntil(), l.getDocLocation(),
                l.getWriterName(), l.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("시설 점검 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
