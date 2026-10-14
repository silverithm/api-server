package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 목욕 제공 일지 요청·응답. 분(minutes)은 응답에만 있다 — 서버가 시작·끝 시각으로 계산한다. */
public final class LabBathLogDto {

    private LabBathLogDto() {
    }

    public record Input(Long elderlyId, LocalDate bathDate, String startTime, String endTime, String method,
                        String providerName, String preCheck, String skinNote, String note) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate bathDate, String startTime,
                       String endTime, int minutes, String method, String providerName, String preCheck,
                       String skinNote, String note, LocalDateTime updatedAt) {
    }
}
