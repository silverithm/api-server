package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * "메시지 주변 조회"(around) 결과.
 *
 * 검색 결과 등에서 현재 화면에 로드되지 않은 과거 메시지로 바로 이동할 때 쓴다.
 * messages는 기존 메시지 목록 조회(ChatService.getMessages)와 같은 모양(최신순 정렬)이라
 * 프론트가 같은 렌더링 경로를 그대로 쓸 수 있다. hasBefore/hasAfter는 기존 목록 조회의
 * hasMore와 같은 관례(요청한 만큼 꽉 찼으면 더 있다고 본다)를 앞/뒤 각각에 적용한 것이다.
 */
@Getter
@AllArgsConstructor
public class ChatMessagesAroundDTO {
    private List<ChatMessageDTO> messages;
    private boolean hasBefore;
    private boolean hasAfter;
}
