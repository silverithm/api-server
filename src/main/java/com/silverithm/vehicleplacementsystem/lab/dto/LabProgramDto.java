package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 프로그램 일지 요청·응답. participantNames는 서버가 채우므로 입력에는 없다.
 * durationMinutes·cognitiveActivity·providerKind는 12차에 더했다 — 옛 요청에 없으면 null·false다.
 */
public final class LabProgramDto {

    private LabProgramDto() {
    }

    public record Input(LocalDate programDate, String startTime, String category, String title, String goal,
                        String content, String reaction, String instructorName, List<Long> participantIds,
                        Boolean aiAssisted, Integer durationMinutes, Boolean cognitiveActivity,
                        String providerKind) {
    }

    public record View(Long id, LocalDate programDate, String startTime, String category, String title,
                       String goal, String content, String reaction, String instructorName,
                       List<Long> participantIds, List<String> participantNames, boolean aiAssisted,
                       LocalDateTime updatedAt, Integer durationMinutes, boolean cognitiveActivity,
                       String providerKind) {
    }
}
