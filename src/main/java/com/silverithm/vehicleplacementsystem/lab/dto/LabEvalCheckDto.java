package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDateTime;

/** 기관평가 준비 점검 요청·응답. */
public final class LabEvalCheckDto {

    private LabEvalCheckDto() {
    }

    public record Input(String status, String evidence, String aiComment) {
    }

    public record View(String indicatorCode, String status, String evidence, String aiComment,
                       String updatedBy, LocalDateTime updatedAt) {
    }
}
