package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 외부기관·지역자원 연계 요청·응답. elderlyId가 null이면 기관 전체 연계다. */
public final class LabResourceLinkDto {

    private LabResourceLinkDto() {
    }

    public record Input(Long elderlyId, LocalDate linkedOn, String resourceType, String institution,
                        String contactPerson, String content, String outcome, LocalDate followUpOn,
                        Boolean referralGiven, String writerName, Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate linkedOn, String resourceType,
                       String institution, String contactPerson, String content, String outcome,
                       LocalDate followUpOn, boolean referralGiven, String writerName, boolean aiAssisted,
                       LocalDateTime updatedAt) {
    }
}
