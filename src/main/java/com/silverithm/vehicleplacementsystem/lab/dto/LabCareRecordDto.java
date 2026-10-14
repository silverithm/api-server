package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 급여제공기록 요청·응답. JSON 필드명은 웹과 맞춘 계약이라 바꾸지 않는다. */
public final class LabCareRecordDto {

    private LabCareRecordDto() {
    }

    public record Input(Long elderlyId, LocalDate recordDate, String physical, String cognitive, String meal,
                        String toilet, String bath, String nursing, String training, String specialNote,
                        String rawNote, String providerName, Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate recordDate, String physical,
                       String cognitive, String meal, String toilet, String bath, String nursing,
                       String training, String specialNote, String rawNote, String providerName,
                       boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
