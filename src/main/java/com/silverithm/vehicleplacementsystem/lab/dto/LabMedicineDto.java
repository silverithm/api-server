package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 약품 관리대장·보관 점검 요청·응답. 점검 항목은 {stockId, ok, action, note}. */
public final class LabMedicineDto {

    private LabMedicineDto() {
    }

    public record StockInput(String name, String efficacy, Integer quantity, String unit, String storage,
                             LocalDate expiresOn, LocalDate openedOn, LocalDate disposedOn, String note) {
    }

    public record StockView(Long id, String name, String efficacy, int quantity, String unit, String storage,
                            LocalDate expiresOn, LocalDate openedOn, LocalDate disposedOn, String note,
                            LocalDateTime updatedAt) {
    }

    public record CheckItem(Long stockId, Boolean ok, String action, String note) {
    }

    public record CheckInput(LocalDate checkedOn, String inspectorName, Boolean cabinetLocked, Boolean fridgeLocked,
                             List<CheckItem> items, String note) {
    }

    public record CheckView(Long id, LocalDate checkedOn, String inspectorName, boolean cabinetLocked,
                            Boolean fridgeLocked, List<CheckItem> items, String note, LocalDateTime updatedAt) {
    }
}
