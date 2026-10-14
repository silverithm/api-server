package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 진료 동행 기록 요청·응답. elderlyName은 서버가 채우므로 입력에는 없다. */
public final class LabMedicalVisitDto {

    private LabMedicalVisitDto() {
    }

    public record Input(Long elderlyId, LocalDate visitDate, String startTime, String endTime, String hospital,
                        String department, String reason, String result, String prescription, String escortName,
                        Boolean guardianNotified, String guardianNote, LocalDate followUpOn, Boolean followUpDone,
                        Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate visitDate, String startTime,
                       String endTime, String hospital, String department, String reason, String result,
                       String prescription, String escortName, boolean guardianNotified, String guardianNote,
                       LocalDate followUpOn, boolean followUpDone, boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
