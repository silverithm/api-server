package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 1:1 방 이름은 보는 사람의 상대 이름으로 부른다 (2026-10-09 버그제보방
 * "개인간의 채팅방에서는 대화방 이름을 상대방 이름이 보이도록 해주세요").
 *
 * 실제로 있던 모습 — 김보경 님이 박소향 님에게 1:1 대화를 열면 방 이름이 "박소향 님과의 대화"로 저장되고,
 * 박소향 님 목록에도 그 이름(자기 이름)이 그대로 떴다.
 */
class DirectRoomNameTest {

    private static ChatParticipant 참가자(String userId, Long memberId, Long appUserId, String name) {
        return ChatParticipant.builder()
                .userId(userId).memberId(memberId).appUserId(appUserId)
                .userName(name).isActive(true)
                .build();
    }

    private static final ChatParticipant 김보경 = 참가자("21", 21L, null, "김보경");
    private static final ChatParticipant 박소향 = 참가자("34", 34L, null, "박소향");

    private static ChatRoom 방(String name, String description, ChatParticipant... people) {
        ChatRoom room = ChatRoom.builder().name(name).description(description).build();
        room.getParticipants().addAll(List.of(people));
        return room;
    }

    @Test
    @DisplayName("받은 사람에게는 만든 사람 이름으로, 만든 사람에게는 그대로 보인다")
    void 양쪽모두_상대이름() {
        ChatRoom room = 방("박소향 님과의 대화", "1:1 대화", 김보경, 박소향);

        assertThat(DirectRoomName.forViewer(room, "34")).isEqualTo("김보경 님과의 대화");
        assertThat(DirectRoomName.forViewer(room, "21")).isEqualTo("박소향 님과의 대화");
    }

    @Test
    @DisplayName("관리자 계정(admin_<id>)도 상대 이름으로 본다")
    void 관리자계정() {
        ChatParticipant 원장 = 참가자("admin_3", null, 3L, "김도형");
        ChatRoom room = 방("이수나 님과의 대화", "1:1 대화", 원장, 참가자("40", 40L, null, "이수나"));

        assertThat(DirectRoomName.forViewer(room, "40")).isEqualTo("김도형 님과의 대화");
        assertThat(DirectRoomName.forViewer(room, "admin_3")).isEqualTo("이수나 님과의 대화");
    }

    @Test
    @DisplayName("참조 칼럼이 빈 옛 참가 행도 문자열 id로 알아본다")
    void 옛참가행() {
        ChatRoom room = 방("박소향 님과의 대화", "1:1 대화",
                참가자("21", null, null, "김보경"), 참가자("34", null, null, "박소향"));

        assertThat(DirectRoomName.forViewer(room, "34")).isEqualTo("김보경 님과의 대화");
    }

    @Test
    @DisplayName("이름을 직접 바꾼 1:1 방은 바꾼 이름 그대로")
    void 직접바꾼이름() {
        ChatRoom room = 방("야간 인수인계", "1:1 대화", 김보경, 박소향);

        assertThat(DirectRoomName.forViewer(room, "34")).isEqualTo("야간 인수인계");
    }

    @Test
    @DisplayName("1:1 대화로 만든 방이 아니면(직접 만든 둘만의 방) 그대로")
    void 일반방() {
        ChatRoom room = 방("박소향 님과의 대화", null, 김보경, 박소향);

        assertThat(DirectRoomName.forViewer(room, "34")).isEqualTo("박소향 님과의 대화");
    }

    @Test
    @DisplayName("한 명이 나가 혼자 남았거나 사람이 늘면 그대로")
    void 둘이아니면() {
        ChatParticipant 나간사람 = 참가자("21", 21L, null, "김보경");
        나간사람.setIsActive(false);

        assertThat(DirectRoomName.forViewer(방("박소향 님과의 대화", "1:1 대화", 나간사람, 박소향), "34"))
                .isEqualTo("박소향 님과의 대화");
        assertThat(DirectRoomName.forViewer(
                방("박소향 님과의 대화", "1:1 대화", 김보경, 박소향, 참가자("50", 50L, null, "이수나")), "34"))
                .isEqualTo("박소향 님과의 대화");
    }

    @Test
    @DisplayName("보는 사람을 모르거나 그 방 사람이 아니면 그대로")
    void 보는사람이밖() {
        ChatRoom room = 방("박소향 님과의 대화", "1:1 대화", 김보경, 박소향);

        assertThat(DirectRoomName.forViewer(room, null)).isEqualTo("박소향 님과의 대화");
        assertThat(DirectRoomName.forViewer(room, "admin_1")).isEqualTo("박소향 님과의 대화");
    }
}
