package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 현금출납부 요청·응답. 금액은 Long — 범위 초과는 서비스가 400으로 거절한다. */
public final class LabLedgerDto {

    private LabLedgerDto() {
    }

    public record Input(LocalDate entryDate, String direction, String accountCode, String description,
                        Long amount, String counterparty, String evidenceType, Long donationId,
                        Boolean aiAssisted) {
    }

    public record View(Long id, LocalDate entryDate, String direction, String accountCode, String description,
                       long amount, String counterparty, String evidenceType, Long donationId,
                       boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
