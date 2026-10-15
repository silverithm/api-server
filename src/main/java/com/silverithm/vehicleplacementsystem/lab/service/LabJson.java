package com.silverithm.vehicleplacementsystem.lab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBusinessPlanDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCarePlanDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareRecordDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCopayBillDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMealPlanDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicationDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicineDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabPayrollDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramPlanDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabSafetyCheckDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffingDto;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 비급여 항목·평가 점수처럼 JSON 문자열 컬럼에 담는 값의 변환. 읽기 실패는 빈 값으로 흘려 목록 전체가 죽지 않게 한다. */
final class LabJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LabJson() {
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 직렬화 실패", e);
        }
    }

    static List<LabCopayBillDto.NonCoveredItem> readItems(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabCopayBillDto.NonCoveredItem>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 평가 점수 — 욕창·낙상은 값이 모두 정수, 인지기능은 도구·판정 같은 문자열도 섞여 있어 값은 Object다 */
    static Map<String, Object> readScores(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    /** id 배열(프로그램 참여자·교육 참석자). 깨진 값은 빈 목록으로 흘린다 */
    static List<Long> readIds(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 욕구사정 — 없거나 깨졌으면 모든 영역이 비어 있는 객체. 11차 새 키(옛 기록엔 없음)는 ''로 돌려준다 */
    static LabCarePlanDto.Needs readNeeds(String json) {
        LabCarePlanDto.Needs needs = null;
        if (json != null && !json.isBlank()) {
            try {
                needs = MAPPER.readValue(json, LabCarePlanDto.Needs.class);
            } catch (JsonProcessingException e) {
                needs = null;
            }
        }
        if (needs == null) {
            needs = new LabCarePlanDto.Needs(null, null, null, null, null, null);
        }
        return new LabCarePlanDto.Needs(needs.physical(), needs.cognition(), needs.nutrition(),
                needs.communication(), needs.family(), needs.other(), blank(needs.disease()), blank(needs.oral()),
                blank(needs.subjective()), blank(needs.resources()), blank(needs.summary()),
                blank(needs.avoidFoods()));
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    static List<LabCarePlanDto.ServiceItem> readServices(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabCarePlanDto.ServiceItem>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 주간 식단 6일 배열. 깨진 값은 빈 목록으로 흘린다 */
    static List<LabMealPlanDto.Day> readMealDays(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabMealPlanDto.Day>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 점검 항목 배열. 깨진 값은 빈 목록으로 흘린다 */
    static List<LabSafetyCheckDto.Item> readSafetyItems(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabSafetyCheckDto.Item>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 기타 수당 JSON. 깨진 값은 빈 목록으로 흘린다 */
    static List<LabPayrollDto.AllowanceView> readAllowances(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabPayrollDto.AllowanceView>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 기타 공제 JSON. 깨진 값은 빈 목록으로 흘린다 */
    static List<LabPayrollDto.DeductionView> readDeductions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabPayrollDto.DeductionView>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 급여제공기록 서식 칸. 옛 기록(null)이거나 깨졌으면 null — 목록 전체가 죽지 않게 한다 */
    static LabCareRecordDto.Form readCareForm(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, LabCareRecordDto.Form.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** {항목 코드: 'YES'|'NO'} — 시설 정보의 적용 여부 직접 지정. 깨진 값은 빈 맵으로 흘린다 */
    static Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    /** 문자열 배열(기능회복훈련 항목). 깨진 값은 빈 목록으로 흘린다 */
    static List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 투약의뢰서의 약 목록. 깨진 값은 빈 목록으로 흘린다 */
    static List<LabMedicationDto.MedicationItem> readMedicationItems(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabMedicationDto.MedicationItem>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 약품 점검 항목. 깨진 값은 빈 목록으로 흘린다 */
    static List<LabMedicineDto.CheckItem> readMedicineCheckItems(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabMedicineDto.CheckItem>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 월간 계획표 항목. 깨진 값은 빈 목록으로 흘린다 */
    static List<LabProgramPlanDto.ScheduleItem> readScheduleItems(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabProgramPlanDto.ScheduleItem>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 사업계획 분야별 항목 — 없거나 깨졌으면 세 분야 모두 빈 목록 */
    static LabBusinessPlanDto.Sections readBusinessSections(String json) {
        LabBusinessPlanDto.Sections empty = new LabBusinessPlanDto.Sections(List.of(), List.of(), List.of());
        if (json == null || json.isBlank()) {
            return empty;
        }
        try {
            return MAPPER.readValue(json, LabBusinessPlanDto.Sections.class);
        } catch (JsonProcessingException e) {
            return empty;
        }
    }

    /** 만족도 점수 {문항코드: 점수}. 깨진 값은 빈 맵으로 흘린다 */
    static Map<String, Integer> readSatisfactionScores(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    /** 인력배치 직원 목록. 깨진 값은 빈 목록으로 흘린다 */
    static List<LabStaffingDto.Staff> readStaffingStaff(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabStaffingDto.Staff>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 객체 배열(사례관리 대상·송영 탑승자 등). 깨진 값은 빈 목록으로 흘린다 */
    static <T> List<T> readList(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, type));
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** {항목 키: boolean} — 규정 대장의 점검 결과. 깨진 값은 빈 맵으로 흘린다 */
    static Map<String, Boolean> readChecks(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Boolean>>() {
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }
}
