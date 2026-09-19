package com.silverithm.vehicleplacementsystem.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatParticipantLastReadTest {

    private ChatParticipant participant() {
        return ChatParticipant.builder().build();
    }

    @Test
    void 앞으로만_움직인다() {
        ChatParticipant p = participant();
        p.updateLastRead(100L);
        p.updateLastRead(90L);
        assertThat(p.getLastReadMessageId()).isEqualTo(100L);
        p.updateLastRead(120L);
        assertThat(p.getLastReadMessageId()).isEqualTo(120L);
    }

    @Test
    void 보내는_중인_메시지의_음수_임시번호는_무시한다() {
        ChatParticipant p = participant();
        p.updateLastRead(4566L);
        p.updateLastRead(-1789712935621L);
        assertThat(p.getLastReadMessageId()).isEqualTo(4566L);
        p.updateLastRead(0L);
        p.updateLastRead(null);
        assertThat(p.getLastReadMessageId()).isEqualTo(4566L);
    }

    @Test
    void 이미_음수로_망가진_위치는_정상값이_오면_바로잡힌다() {
        ChatParticipant p = participant();
        p.updateLastRead(10L);
        // 옛 서버가 남긴 음수 값을 흉내 낸다
        org.springframework.test.util.ReflectionTestUtils.setField(p, "lastReadMessageId", -5L);
        p.updateLastRead(3L);
        assertThat(p.getLastReadMessageId()).isEqualTo(3L);
    }
}
