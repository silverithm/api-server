package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사진을 한 번에 여러 장 보내면 알림도 한 번만 가야 한다.
 *
 * 열 장을 고르면 메시지는 열 건으로 나뉘어 저장되는데, 알림까지 열 번 울리면
 * 화면에 묶음 하나로 보이는 것과 앞뒤가 맞지 않는다.
 */
class ChatMessageBatchCollectorTest {

    private record Sent(Long roomId, Long lastMessageId, int count) {}

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private ChatMessageBatchCollector collector;

    private ChatMessageBatchCollector collectorWaiting(long waitMillis) {
        collector = new ChatMessageBatchCollector(
                (roomId, lastMessageId, count) -> sent.add(new Sent(roomId, lastMessageId, count)),
                waitMillis);
        return collector;
    }

    @AfterEach
    void tearDown() {
        if (collector != null) collector.shutdown();
    }

    @Test
    @DisplayName("세 장을 한 번에 보내면 알림은 마지막 장이 올라온 뒤 한 번만 나간다")
    void sendsOnceWhenBatchCompletes() throws Exception {
        ChatMessageBatchCollector c = collectorWaiting(10_000L);

        c.collect(1L, 11L, "batch-a", 3);
        c.collect(1L, 12L, "batch-a", 3);
        assertThat(sent).as("아직 마지막 장이 안 왔다").isEmpty();

        c.collect(1L, 13L, "batch-a", 3);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).isEqualTo(new Sent(1L, 13L, 3));
    }

    @Test
    @DisplayName("중간에 한 장이 실패해 묶음이 안 채워져도 알림이 사라지지는 않는다")
    void safetyNetSendsWhatArrived() throws Exception {
        ChatMessageBatchCollector c = collectorWaiting(150L);

        c.collect(1L, 11L, "batch-b", 3);
        c.collect(1L, 12L, "batch-b", 3);

        Thread.sleep(600);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).isEqualTo(new Sent(1L, 12L, 2));
    }

    @Test
    @DisplayName("다른 묶음끼리는 섞이지 않는다 — 각각 한 번씩")
    void batchesAreIndependent() {
        ChatMessageBatchCollector c = collectorWaiting(10_000L);

        c.collect(1L, 11L, "batch-c", 2);
        c.collect(2L, 21L, "batch-d", 2);
        c.collect(2L, 22L, "batch-d", 2);
        c.collect(1L, 12L, "batch-c", 2);

        assertThat(sent).containsExactly(new Sent(2L, 22L, 2), new Sent(1L, 12L, 2));
    }

    @Test
    @DisplayName("묶음이 다 나간 뒤 늦게 온 한 장은 따로 처리된다 — 안전망 시간 뒤 한 번")
    void lateArrivalStartsNewBatch() throws Exception {
        ChatMessageBatchCollector c = collectorWaiting(150L);

        c.collect(1L, 11L, "batch-e", 2);
        c.collect(1L, 12L, "batch-e", 2);
        assertThat(sent).hasSize(1);

        c.collect(1L, 13L, "batch-e", 2);
        Thread.sleep(600);

        assertThat(sent).hasSize(2);
        assertThat(sent.get(1)).isEqualTo(new Sent(1L, 13L, 1));
    }

    @Test
    @DisplayName("묶음 정보가 없거나 한 장뿐이면 묶지 않는다")
    void notBatchedWithoutBatchInfo() {
        assertThat(ChatMessageBatchCollector.isBatched(null, 5)).isFalse();
        assertThat(ChatMessageBatchCollector.isBatched("  ", 5)).isFalse();
        assertThat(ChatMessageBatchCollector.isBatched("batch", null)).isFalse();
        assertThat(ChatMessageBatchCollector.isBatched("batch", 1)).isFalse();
        assertThat(ChatMessageBatchCollector.isBatched("batch", 2)).isTrue();
    }
}
