package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 입소 상담·대기 요청·응답. */
public final class LabIntakeDto {

    private LabIntakeDto() {
    }

    public record Input(LocalDate inquiryDate, String applicantName, String contactName, String contactPhone,
                        String relation, String careGrade, String desiredDays, String needs, String source,
                        String status, String declineReason, String declineNote, LocalDate consentOn,
                        LocalDate nextContactOn, Long elderlyId, String counselorName, Boolean aiAssisted) {
    }

    public record View(Long id, LocalDate inquiryDate, String applicantName, String contactName,
                       String contactPhone, String relation, String careGrade, String desiredDays, String needs,
                       String source, String status, String declineReason, String declineNote, LocalDate consentOn,
                       LocalDate nextContactOn, Long elderlyId, String elderlyName, String counselorName,
                       boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
