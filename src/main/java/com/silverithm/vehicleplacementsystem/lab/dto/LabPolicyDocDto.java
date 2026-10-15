package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** 규정·지침 대장 요청·응답. checks는 {항목 키: 갖췄는지}. */
public final class LabPolicyDocDto {

    private LabPolicyDocDto() {
    }

    public record Input(String kind, Integer guideNo, String title, LocalDate revisedOn, String location,
                        Map<String, Boolean> checks, LocalDate trainedOn, String note) {
    }

    public record View(Long id, String kind, Integer guideNo, String title, LocalDate revisedOn, String location,
                       Map<String, Boolean> checks, LocalDate trainedOn, String note, LocalDateTime updatedAt) {
    }
}
