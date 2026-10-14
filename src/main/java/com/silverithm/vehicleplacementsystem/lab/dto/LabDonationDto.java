package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 후원금 요청·응답. usedAmount는 입력으로 받지 않는다 — 서버가 연결된 지출 합으로 계산한다. */
public final class LabDonationDto {

    private LabDonationDto() {
    }

    public record Input(LocalDate receivedOn, String donorName, String donorType, String kind, Long amount,
                        String goodsDescription, String designatedPurpose, String receiptNo) {
    }

    public record View(Long id, LocalDate receivedOn, String donorName, String donorType, String kind,
                       long amount, String goodsDescription, String designatedPurpose, String receiptNo,
                       long usedAmount, LocalDateTime updatedAt) {
    }
}
