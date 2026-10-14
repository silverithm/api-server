package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 급여 계약·장기요양 인정 요청·응답. */
public final class LabElderContractDto {

    private LabElderContractDto() {
    }

    public record Input(Long elderlyId, LocalDate contractStart, LocalDate contractEnd, String careGrade,
                        String recognitionNo, LocalDate validFrom, LocalDate validTo, Integer renewalCount,
                        String copayClass, Integer plannedDaysPerMonth, LocalDate signedOn, Boolean copyGiven,
                        LocalDate contractNotifiedOn, LocalDate planNotifiedOn, LocalDate rightsGuidedOn,
                        String status, String endReason, String note) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate contractStart,
                       LocalDate contractEnd, String careGrade, String recognitionNo, LocalDate validFrom,
                       LocalDate validTo, int renewalCount, String copayClass, Integer plannedDaysPerMonth,
                       LocalDate signedOn, boolean copyGiven, LocalDate contractNotifiedOn,
                       LocalDate planNotifiedOn, LocalDate rightsGuidedOn, String status, String endReason,
                       String note, LocalDateTime updatedAt) {
    }
}
