package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 봉사자 활동 요청·응답. 봉사 시간(minutes)은 응답에만 있고 서버가 계산한다. */
public final class LabVolunteerLogDto {

    private LabVolunteerLogDto() {
    }

    public record Input(LocalDate activityDate, String volunteerName, String organization, String startTime,
                        String endTime, String activityType, String content, Boolean vmsRegistered,
                        String confirmerName) {
    }

    public record View(Long id, LocalDate activityDate, String volunteerName, String organization,
                       String startTime, String endTime, int minutes, String activityType, String content,
                       boolean vmsRegistered, String confirmerName, LocalDateTime updatedAt) {
    }
}
