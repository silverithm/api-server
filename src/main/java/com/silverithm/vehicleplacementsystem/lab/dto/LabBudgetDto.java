package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 예산 요청·응답. 줄은 저장할 때마다 통째로 바뀐다. */
public final class LabBudgetDto {

    private LabBudgetDto() {
    }

    public record LineInput(String direction, String accountCode, Long amount, String note) {
    }

    public record Input(Integer fiscalYear, Integer revision, LocalDate decidedOn, String note,
                        List<LineInput> lines) {
    }

    public record LineView(String direction, String accountCode, long amount, String note) {
    }

    public record View(Long id, int fiscalYear, int revision, LocalDate decidedOn, String status, String note,
                       LocalDateTime confirmedAt, List<LineView> lines, LocalDateTime updatedAt) {
    }
}
