package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 예방접종 요청·응답. elderlyName·writerName은 서버가 채우므로 입력에는 없다. */
public final class LabVaccinationDto {

    private LabVaccinationDto() {
    }

    public record Input(Long elderlyId, Integer seasonYear, String vaccine, String status, LocalDate givenOn,
                        String institution, String verifiedBy, String reason, String note) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, int seasonYear, String vaccine, String status,
                       LocalDate givenOn, String institution, String verifiedBy, String reason, String note,
                       String writerName, LocalDateTime updatedAt) {
    }
}
