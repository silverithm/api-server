package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 퇴소 연계기록지 요청·응답. 제공대장 네 칸(providedOn·providedMethod·providerName·recipientName)은 함께 있거나 없다. */
public final class LabDischargeReferralDto {

    private LabDischargeReferralDto() {
    }

    public record Input(Long elderlyId, Long contractId, LocalDate dischargeOn, String reason, LocalDate writtenOn,
                        String careGrade, String functionalStatus, String servicesProvided, String afterPlan,
                        String counselNote, LocalDate providedOn, String providedMethod, String providerName,
                        String recipientName, String writerName, Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, Long contractId, LocalDate dischargeOn,
                       String reason, LocalDate writtenOn, String careGrade, String functionalStatus,
                       String servicesProvided, String afterPlan, String counselNote, LocalDate providedOn,
                       String providedMethod, String providerName, String recipientName, String writerName,
                       boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
