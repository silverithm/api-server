package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 급여제공계획 변경 이력 요청·응답. 어르신 이름·작성자는 서버가 채운다. */
public final class LabCarePlanChangeDto {

    private LabCarePlanChangeDto() {
    }

    public record Input(Long elderlyId, LocalDate changedOn, String reason, String reasonNote,
                        List<String> changeItems, String changeDetail, Long eventId,
                        LocalDate guardianExplainedOn, LocalDate guardianSignedOn, LocalDate notifiedOn) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, LocalDate changedOn, String reason,
                       String reasonNote, List<String> changeItems, String changeDetail, Long eventId,
                       LocalDate guardianExplainedOn, LocalDate guardianSignedOn, LocalDate notifiedOn,
                       String writerName, LocalDateTime updatedAt) {
    }
}
