package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** 위험도 평가 요청·응답. scores는 항목명 → 점수 객체다. */
public final class LabRiskDto {

    private LabRiskDto() {
    }

    /** 점수는 Number로 받아 3.5 같은 소수가 조용히 3으로 깎이지 않게 서비스에서 정수 여부를 직접 본다 */
    public record Input(Long elderlyId, String kind, Map<String, Number> scores, LocalDate assessedOn,
                        String assessorName, String note, Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, String kind, Map<String, Integer> scores,
                       int total, String riskLevel, LocalDate assessedOn, String assessorName, String note,
                       boolean aiAssisted, LocalDateTime createdAt) {
    }

    public record Summary(LocalDate assessedOn, int total, String riskLevel) {
    }

    /** 어르신 1명의 최신 평가 요약 — 한 번도 평가하지 않았으면 braden·morse가 null이다 */
    public record Latest(Long elderlyId, String elderlyName, Summary braden, Summary morse) {
    }
}
