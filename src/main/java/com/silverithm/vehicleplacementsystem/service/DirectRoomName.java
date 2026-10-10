package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatPersonRef;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import java.util.List;

/**
 * 1:1 방 이름을 보는 사람 쪽에서 부른다.
 *
 * <p>앱·웹의 '1:1 대화'는 만든 사람 기준으로 "{상대} 님과의 대화"를 방 이름으로 저장한다.
 * 그대로 내보내면 상대방 목록에는 자기 이름이 방 이름으로 뜬다 — 2026-10-09 버그제보방
 * "개인간의 채팅방에서는 대화방 이름을 상대방 이름이 보이도록 해주세요".
 * 또 양쪽 클라이언트는 이 이름으로 이미 있는 1:1 방을 찾으므로, 받은 쪽에서 1:1 대화를 누르면
 * 방을 못 찾고 같은 두 사람의 방이 하나 더 생길 수 있었다.
 *
 * <p>그래서 둘만 있는 1:1 방이면 보는 사람의 상대 이름으로 바꿔 내려준다. 저장된 이름은 그대로다.
 * 이름을 직접 바꾼 방(규칙에서 벗어난 이름)이나 한 명이 나가 혼자 남은 방은 저장된 이름을 쓴다.
 */
public final class DirectRoomName {

    /** 앱(chat_member_list.dart)·웹(directChat.ts)이 1:1 방을 만들 때 넣는 설명 */
    static final String DIRECT_DESCRIPTION = "1:1 대화";

    /** 앱·웹의 directRoomName 규칙: "{이름} 님과의 대화" */
    static final String SUFFIX = " 님과의 대화";

    private DirectRoomName() {
    }

    public static String forViewer(ChatRoom room, String viewerChatUserId) {
        List<ChatParticipant> active = room.getParticipants() == null ? List.of()
                : room.getParticipants().stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).toList();
        return forViewer(room.getName(), room.getDescription(), active, viewerChatUserId);
    }

    static String forViewer(String storedName, String description,
                            List<ChatParticipant> activeParticipants, String viewerChatUserId) {
        if (storedName == null || !storedName.endsWith(SUFFIX) || !DIRECT_DESCRIPTION.equals(description)
                || activeParticipants.size() != 2) {
            return storedName;
        }

        ChatPersonRef viewer = ChatPersonRef.of(viewerChatUserId);
        if (viewer.refId() == null) {
            return storedName;
        }

        ChatParticipant other = null;
        boolean viewerIsIn = false;
        for (ChatParticipant p : activeParticipants) {
            if (viewer.equals(personOf(p))) {
                viewerIsIn = true;
            } else {
                other = p;
            }
        }

        if (!viewerIsIn || other == null || other.getUserName() == null || other.getUserName().isBlank()) {
            return storedName;
        }
        return other.getUserName().trim() + SUFFIX;
    }

    /** 참조 칼럼이 비어 있는 옛 행은 문자열 id로 판단한다 */
    private static ChatPersonRef personOf(ChatParticipant p) {
        if (p.getMemberId() != null || p.getAppUserId() != null) {
            return ChatPersonRef.of(p.getMemberId(), p.getAppUserId());
        }
        return ChatPersonRef.of(p.getUserId());
    }
}
