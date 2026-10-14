package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 공단 청구 대조 요청·응답. status는 서버가 정하므로 입력의 status는 RESOLVED(확인 완료) 표시에만 쓰인다. */
public final class LabClaimReviewDto {

    private LabClaimReviewDto() {
    }

    public record Input(Long elderlyId, String claimMonth, Integer claimedDays, Integer attendedDays,
                        Integer billedDays, String claimedBand, String status, String note, String reviewerName) {
    }

    public record BatchInput(String month, List<Input> rows) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, String claimMonth, int claimedDays,
                       int attendedDays, Integer billedDays, String claimedBand, String status, String note,
                       String reviewerName, LocalDateTime updatedAt) {
    }
}
