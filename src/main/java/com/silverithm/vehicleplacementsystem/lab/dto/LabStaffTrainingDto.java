package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 직원 교육·회의 요청·응답. attendeeNames는 서버가 채우므로 입력에는 없다. */
public final class LabStaffTrainingDto {

    private LabStaffTrainingDto() {
    }

    public record Input(String kind, LocalDate heldOn, String category, String topic, String instructor,
                        List<Long> attendeeIds, String content, Boolean aiAssisted) {
    }

    public record View(Long id, String kind, LocalDate heldOn, String category, String topic, String instructor,
                       List<Long> attendeeIds, List<String> attendeeNames, String content, boolean aiAssisted,
                       LocalDateTime updatedAt) {
    }
}
