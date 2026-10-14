package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 통장·월말 잔액 요청·응답. accountName은 서버가 채우므로 입력에는 없다. */
public final class LabBankDto {

    private LabBankDto() {
    }

    public record AccountInput(String name, String purpose, String bankName, String accountTail,
                               Long openingBalance, LocalDate openingDate, Boolean active) {
    }

    public record AccountView(Long id, String name, String purpose, String bankName, String accountTail,
                              long openingBalance, LocalDate openingDate, boolean active,
                              LocalDateTime updatedAt) {
    }

    public record StatementInput(Long accountId, String statementMonth, Long closingBalance, String note) {
    }

    public record StatementView(Long id, Long accountId, String accountName, String statementMonth,
                                long closingBalance, String note, LocalDateTime updatedAt) {
    }
}
