package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 기능회복훈련 요청·응답. */
public final class LabRehabSessionDto {

    private LabRehabSessionDto() {
    }

    public record Input(Long elderlyId, LocalDate sessionDate, String kind, Integer minutes, List<String> items,
                        String content, String reaction, String therapistName, Boolean aiAssisted) {
    }

    /** 여러 어르신에게 같은 훈련을 한 번에 — 반응은 어르신마다 달라 나중에 따로 고친다 */
    public record BatchInput(List<Long> elderlyIds, LocalDate sessionDate, String kind, Integer minutes,
                             List<String> items, String content, String therapistName, Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate sessionDate, String kind,
                       int minutes, List<String> items, String content, String reaction, String therapistName,
                       boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
