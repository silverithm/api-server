package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.lab.dto.LabCareRecordDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 급여제공기록 서식 칸(CareRecordForm)의 검증·정리.
 *
 * <p>계약의 키·범위·열거만 받는다. 모르는 키는 정리 결과에 들어가지 않으므로 조용히 버려지고, 값이 틀리면 400이다.
 * 제공 직원이 같은 기관 소속인지는 직원 조회가 필요해 서비스가 이 결과의 {@link #writerIds}로 따로 확인한다.
 */
final class LabCareForm {

    static final List<String> AREAS = List.of("PHYSICAL", "COGNITIVE", "HEALTH", "REHAB");
    static final Set<String> BATH_METHODS = Set.of("FULL", "SHOWER");
    static final Set<String> MEAL_KINDS = Set.of("NORMAL", "PORRIDGE", "LIQUID");
    static final Set<String> MEAL_INTAKES = Set.of("FULL", "HALF_OR_MORE", "LESS_THAN_HALF");
    /** 고시 제30조④⑧·제32조③⑩⑪가 기록지에 적으라고 정한 사유 */
    static final Set<String> REASON_KINDS = Set.of("AFTER_MIDNIGHT", "OVER_13H", "UNUSED_DAY", "COG_PROGRAM_MISSED");

    private static final BigDecimal TEMP_MIN = new BigDecimal("34.0");
    private static final BigDecimal TEMP_MAX = new BigDecimal("42.0");
    private static final int VEHICLE_NO = 20;
    private static final int PROGRAM_NAMES = 200;
    private static final int REASON_TEXT = 300;

    private LabCareForm() {
    }

    /** 검증을 마친 서식 칸 — 저장할 모양(writerNames 없음) */
    static LabCareRecordDto.Form normalize(LabCareRecordDto.Form in) {
        Integer systolic = range(in.bpSystolic(), 50, 260, "수축기 혈압");
        Integer diastolic = range(in.bpDiastolic(), 30, 160, "이완기 혈압");
        if ((systolic == null) != (diastolic == null)) {
            throw LabValidation.bad("혈압은 수축기와 이완기를 함께 입력해 주세요");
        }
        return new LabCareRecordDto.Form(
                clockOrNull(in.startTime(), "이용 시작 시각"), clockOrNull(in.endTime(), "이용 종료 시각"),
                flag(in.transport()), LabValidation.text(in.vehicleNo(), VEHICLE_NO, "차량번호"),
                flag(in.washHelp()), range(in.bathMinutes(), 0, 180, "목욕 시간(분)"),
                optionalOneOf(in.bathMethod(), BATH_METHODS, "목욕 방법"),
                optionalOneOf(in.mealKind(), MEAL_KINDS, "식사 종류"),
                optionalOneOf(in.mealIntake(), MEAL_INTAKES, "식사 섭취량"),
                range(in.toiletCount(), 0, 30, "화장실 이용 횟수"), flag(in.mobilityHelp()),
                flag(in.cogSupport()), flag(in.commHelp()),
                systolic, diastolic, temperature(in.temperature()),
                range(in.healthCareMinutes(), 0, 600, "건강관리 시간(분)"),
                range(in.nursingCareMinutes(), 0, 600, "간호관리 시간(분)"), flag(in.emergency()),
                LabValidation.text(in.programNames(), PROGRAM_NAMES, "프로그램 이름"),
                flag(in.rehabPhysical()), flag(in.rehabCogActivity()), flag(in.rehabCogTraining()),
                flag(in.rehabPtOt()),
                writers(in.writers()), null, reasons(in.reasons()));
    }

    /** 정리된 서식 칸에 적힌 제공 직원 id(중복 없이, 영역 순서대로) */
    static List<Long> writerIds(LabCareRecordDto.Form form) {
        Set<Long> ids = new LinkedHashSet<>();
        if (form.writers() == null) {
            return List.of();
        }
        for (String area : AREAS) {
            Long id = form.writers().get(area);
            if (id != null) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    /** 응답용 — 제공 직원 이름을 붙인다. 이름을 모르는 영역(직원이 없어졌거나 비움)은 null */
    static LabCareRecordDto.Form withNames(LabCareRecordDto.Form form, Map<Long, String> names) {
        Map<String, Long> writers = writers(form.writers());
        Map<String, String> writerNames = new LinkedHashMap<>();
        for (String area : AREAS) {
            Long id = writers.get(area);
            writerNames.put(area, id == null ? null : names.get(id));
        }
        return new LabCareRecordDto.Form(form.startTime(), form.endTime(), flag(form.transport()), form.vehicleNo(),
                flag(form.washHelp()), form.bathMinutes(), form.bathMethod(), form.mealKind(), form.mealIntake(),
                form.toiletCount(), flag(form.mobilityHelp()), flag(form.cogSupport()), flag(form.commHelp()),
                form.bpSystolic(), form.bpDiastolic(), form.temperature(), form.healthCareMinutes(),
                form.nursingCareMinutes(), flag(form.emergency()), form.programNames(),
                flag(form.rehabPhysical()), flag(form.rehabCogActivity()), flag(form.rehabCogTraining()),
                flag(form.rehabPtOt()), writers, writerNames,
                form.reasons() == null ? List.of() : form.reasons());
    }

    private static Boolean flag(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private static Integer range(Integer value, int min, int max, String label) {
        if (value == null) {
            return null;
        }
        return (int) LabValidation.longInRange(value.longValue(), min, max, label);
    }

    private static String optionalOneOf(String value, Set<String> allowed, String label) {
        return value == null || value.isBlank() ? null : LabValidation.oneOf(value, allowed, label);
    }

    private static String clockOrNull(String value, String label) {
        return value == null || value.isBlank() ? null : LabValidation.clock(value, label);
    }

    /** 34.0~42.0, 소수 첫째 자리까지. 36.55처럼 둘째 자리가 있으면 400 — 반올림하면 기록이 조용히 바뀐다 */
    private static BigDecimal temperature(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.stripTrailingZeros().scale() > 1) {
            throw LabValidation.bad("체온은 소수 첫째 자리까지 입력해 주세요 (예: 36.5)");
        }
        if (value.compareTo(TEMP_MIN) < 0 || value.compareTo(TEMP_MAX) > 0) {
            throw LabValidation.bad("체온은 34.0~42.0 사이여야 합니다");
        }
        return value.setScale(1);
    }

    /** 네 영역 모두 키를 가진 맵(없으면 null). 모르는 영역 키는 버린다 */
    private static Map<String, Long> writers(Map<String, Long> in) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String area : AREAS) {
            out.put(area, in == null ? null : in.get(area));
        }
        return out;
    }

    private static List<LabCareRecordDto.Reason> reasons(List<LabCareRecordDto.Reason> in) {
        List<LabCareRecordDto.Reason> out = new ArrayList<>();
        if (in == null) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (LabCareRecordDto.Reason reason : in) {
            if (reason == null) {
                throw LabValidation.bad("사유 목록에 올바르지 않은 값이 있습니다");
            }
            String kind = LabValidation.oneOf(reason.kind(), REASON_KINDS, "사유 종류");
            String text = LabValidation.text(reason.text(), REASON_TEXT, "사유");
            if (text == null) {
                throw LabValidation.bad("사유 내용을 입력해 주세요");
            }
            if (!seen.add(kind)) {
                throw LabValidation.bad("같은 종류의 사유를 두 번 적을 수 없습니다");
            }
            out.add(new LabCareRecordDto.Reason(kind, text));
        }
        return out;
    }
}
