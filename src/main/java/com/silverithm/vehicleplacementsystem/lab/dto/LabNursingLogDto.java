package com.silverithm.vehicleplacementsystem.lab.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 간호일지 요청·응답. JSON 필드명은 웹과 맞춘 계약이라 바꾸지 않는다. */
public final class LabNursingLogDto {

    private LabNursingLogDto() {
    }

    /** 숫자는 Integer/BigDecimal로 받아 "abc"나 범위 밖 값이 500이 아니라 400이 되게 서비스가 검증한다 */
    public record Input(Long elderlyId, LocalDate logDate, Integer systolic, Integer diastolic, Integer pulse,
                        BigDecimal temperature, Integer bloodSugar, BigDecimal weight, String medMorning,
                        String medLunch, String medEvening, String treatment, String note, String recorderName,
                        Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate logDate, Integer systolic,
                       Integer diastolic, Integer pulse, BigDecimal temperature, Integer bloodSugar,
                       BigDecimal weight, String medMorning, String medLunch, String medEvening,
                       String treatment, String note, String recorderName, boolean aiAssisted,
                       LocalDateTime updatedAt) {
    }
}
