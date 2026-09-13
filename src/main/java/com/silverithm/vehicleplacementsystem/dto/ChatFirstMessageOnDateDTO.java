package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * "날짜로 이동" 조회 결과.
 *
 * 카톡처럼 채팅 검색에서 날짜를 지정하면 그 날짜의 대화 처음으로 이동하는 기능(버그제보 2026-09-10,
 * 배정민)에 쓴다. 클라이언트는 이 messageId로 기존 "메시지 주변 조회"(around)를 불러 그 자리로
 * 이동한다 — 그래서 messageId만 있으면 충분하고, createdAt은 화면에 날짜 구분선을 바로 그릴 수 있게
 * 함께 내려준다.
 */
@Getter
@AllArgsConstructor
public class ChatFirstMessageOnDateDTO {
    private Long messageId;
    private LocalDateTime createdAt;
}
