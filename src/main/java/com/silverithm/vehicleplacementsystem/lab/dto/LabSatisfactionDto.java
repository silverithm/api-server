package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** 급여 만족도 응답 요청·응답. scores는 {문항코드: 1~5}. elderlyId가 null이면 익명 응답이다. */
public final class LabSatisfactionDto {

    private LabSatisfactionDto() {
    }

    public record Input(LocalDate surveyDate, String kind, Long elderlyId, String respondent,
                        Map<String, Integer> scores, String comment, String interviewerName) {
    }

    public record View(Long id, LocalDate surveyDate, String kind, Long elderlyId, String elderlyName,
                       String respondent, Map<String, Integer> scores, String comment, String interviewerName,
                       LocalDateTime updatedAt) {
    }
}
