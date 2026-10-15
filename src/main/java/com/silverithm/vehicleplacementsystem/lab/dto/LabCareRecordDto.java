package com.silverithm.vehicleplacementsystem.lab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 급여제공기록 요청·응답. JSON 필드명은 웹과 맞춘 계약이라 바꾸지 않는다. */
public final class LabCareRecordDto {

    private LabCareRecordDto() {
    }

    public record Input(Long elderlyId, LocalDate recordDate, String physical, String cognitive, String meal,
                        String toilet, String bath, String nursing, String training, String specialNote,
                        String rawNote, String providerName, Boolean aiAssisted, Form form) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate recordDate, String physical,
                       String cognitive, String meal, String toilet, String bath, String nursing,
                       String training, String specialNote, String rawNote, String providerName,
                       boolean aiAssisted, LocalDateTime updatedAt, Form form) {
    }

    /**
     * 별지 제15호 서식 칸(CareRecordForm). 키 이름은 웹과 맞춘 계약이라 바꾸지 않는다.
     * 서버는 이 모양만 저장하고 모르는 키는 버린다. writerNames는 서버가 응답에만 채운다(입력·저장에는 없다).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Form(String startTime, String endTime, Boolean transport, String vehicleNo,
                       Boolean washHelp, Integer bathMinutes, String bathMethod, String mealKind,
                       String mealIntake, Integer toiletCount, Boolean mobilityHelp,
                       Boolean cogSupport, Boolean commHelp,
                       Integer bpSystolic, Integer bpDiastolic, BigDecimal temperature,
                       Integer healthCareMinutes, Integer nursingCareMinutes, Boolean emergency,
                       String programNames, Boolean rehabPhysical, Boolean rehabCogActivity,
                       Boolean rehabCogTraining, Boolean rehabPtOt,
                       Map<String, Long> writers, Map<String, String> writerNames, List<Reason> reasons) {
    }

    /** 고시가 기록지에 적으라고 정한 사유 — kind는 종류별로 하나만 둘 수 있다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reason(String kind, String text) {
    }
}
