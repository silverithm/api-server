package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 상담일지 요청·응답. */
public final class LabConsultationDto {

    private LabConsultationDto() {
    }

    public record Input(Long elderlyId, LocalDate consultedOn, String counterpart, String counterpartName,
                        String method, String content, String followUp, String consultantName,
                        Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate consultedOn, String counterpart,
                       String counterpartName, String method, String content, String followUp,
                       String consultantName, boolean aiAssisted, LocalDateTime createdAt) {
    }
}
