package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 위험도 평가 요청·응답. scores는 항목명 → 값 객체다.
 * 욕창·낙상은 값이 모두 정수지만, 인지기능(COGNITIVE)은 tool·judgment·institution이 문자열이라 값 타입을 열어 둔다.
 */
public final class LabRiskDto {

    private LabRiskDto() {
    }

    /** 점수는 Number로 받아 3.5 같은 소수가 조용히 3으로 깎이지 않게 서비스에서 정수 여부를 직접 본다(인지기능 항목은 문자열) */
    public record Input(Long elderlyId, String kind, Map<String, ?> scores, LocalDate assessedOn,
                        String assessorName, String note, Boolean aiAssisted) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, String kind, Map<String, Object> scores,
                       int total, String riskLevel, LocalDate assessedOn, String assessorName, String note,
                       boolean aiAssisted, LocalDateTime createdAt) {
    }

    public record Summary(LocalDate assessedOn, int total, String riskLevel) {
    }

    /** 어르신 1명의 최신 평가 요약 — 한 번도 평가하지 않았으면 braden·morse·cognitive가 null이다 */
    public record Latest(Long elderlyId, String elderlyName, Summary braden, Summary morse, Summary cognitive) {
    }
}
