package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 위생·안전·시설 점검 요청·응답. */
public final class LabSafetyCheckDto {

    private LabSafetyCheckDto() {
    }

    /** ok: true 적합, false 부적합, null 점검 안 함 */
    public record Item(String code, Boolean ok, String note) {
    }

    public record Input(LocalDate checkDate, String kind, List<Item> items, String actions, String inspectorName,
                        Boolean aiAssisted) {
    }

    public record View(Long id, LocalDate checkDate, String kind, List<Item> items, String actions,
                       String inspectorName, boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
