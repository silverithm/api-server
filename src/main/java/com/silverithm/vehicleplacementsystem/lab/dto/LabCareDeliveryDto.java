package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;

/** 제공대장 요청·응답. 월은 'YYYY-MM' 문자열이다. */
public final class LabCareDeliveryDto {

    private LabCareDeliveryDto() {
    }

    public record Input(Long elderlyId, String month, LocalDate deliveredOn, String method, String recipient,
                        String note) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, String month, LocalDate deliveredOn,
                       String method, String recipient, String note) {
    }
}
