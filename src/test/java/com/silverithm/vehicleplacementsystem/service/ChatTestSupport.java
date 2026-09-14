package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.repository.ChatMessageReadRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ChatRoomRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** 채팅 서비스 테스트가 함께 쓰는 조립 도우미. */
final class ChatTestSupport {

    private ChatTestSupport() {}

    /** 프록시 없는 writer — 테스트 트랜잭션 안에서 저장까지만 확인할 때 쓴다. */
    static ChatMessageWriter writer(ChatRoomRepository rooms, ChatParticipantRepository participants,
                                    ChatMessageRepository messages, ChatMessageReadRepository reads) {
        return new ChatMessageWriter(rooms, participants, messages, reads);
    }

    static ChatSendMetrics metrics() {
        return new ChatSendMetrics(new SimpleMeterRegistry());
    }

    static ChatSendMetrics metrics(SimpleMeterRegistry registry) {
        return new ChatSendMetrics(registry);
    }

    static double count(SimpleMeterRegistry registry, String result) {
        return registry.get("chat.message.send").tag("result", result).counter().count();
    }
}
