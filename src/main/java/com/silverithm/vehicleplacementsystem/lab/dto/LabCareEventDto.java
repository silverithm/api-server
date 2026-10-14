package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 상태변화·사고 요청·응답. 날짜시간은 'YYYY-MM-DDTHH:MM'(초 없음, 서울 시각) 문자열이다. */
public final class LabCareEventDto {

    private LabCareEventDto() {
    }

    public record Input(Long elderlyId, String occurredAt, String kind, String severity, String place,
                        String description, String immediateAction, String guardianNotifiedAt,
                        String guardianMethod, String guardianResponse, String hospitalName, String followUp,
                        String prevention, String status, LocalDate closedOn, String reporterName,
                        Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, String occurredAt, String kind,
                       String severity, String place, String description, String immediateAction,
                       String guardianNotifiedAt, String guardianMethod, String guardianResponse,
                       String hospitalName, String followUp, String prevention, String status,
                       LocalDate closedOn, String reporterName, boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
