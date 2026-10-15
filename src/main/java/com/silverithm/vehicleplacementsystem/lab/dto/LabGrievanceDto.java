package com.silverithm.vehicleplacementsystem.lab.dto;

/**
 * 고충 요약 — 건수와 처리 일수만. 고충의 제목·본문·작성자·답변 글은 실험실로 가져오지 않으므로 이 응답에 담을 칸이 없다.
 * medianReplyDays는 답한 건의 일수 중앙값(소수 첫째 자리, 답한 건이 없으면 null),
 * oldestOpenDays는 조치완료가 아닌 건 중 가장 오래된 것의 경과 일수(없으면 null).
 */
public final class LabGrievanceDto {

    private LabGrievanceDto() {
    }

    public record Summary(int year, int total, int anonymous, int received, int inReview, int resolved, int onHold,
                          int replied, Double medianReplyDays, Integer oldestOpenDays) {
    }
}
