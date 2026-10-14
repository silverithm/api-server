package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 수급자 서류함 요청·응답. elderlyName은 서버가 채우므로 입력에는 없다. */
public final class LabElderDocumentDto {

    private LabElderDocumentDto() {
    }

    public record Input(Long elderlyId, String docType, String title, LocalDate issuedOn, LocalDate expiresOn,
                        String location, LocalDate checkedOn, String note) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, String docType, String title,
                       LocalDate issuedOn, LocalDate expiresOn, String location, LocalDate checkedOn, String note,
                       LocalDateTime updatedAt) {
    }
}
