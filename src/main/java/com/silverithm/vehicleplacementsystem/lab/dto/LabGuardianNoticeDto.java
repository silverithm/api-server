package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 보호자 월간 안내 요청·응답. */
public final class LabGuardianNoticeDto {

    private LabGuardianNoticeDto() {
    }

    public record Input(Long elderlyId, String noticeMonth, String title, String body, String facts,
                        String writerName, Boolean aiAssisted) {
    }

    public record SentInput(LocalDate sentOn, String method, String recipient) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, String noticeMonth, String title,
                       String body, String facts, String status, LocalDate sentOn, String method,
                       String recipient, String writerName, boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
