package com.silverithm.vehicleplacementsystem.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 한 번에 보낸 메시지 묶음을 모아 알림 한 건으로 만든다.
 *
 * 사진 열 장을 한 번에 고르면 메시지는 열 건으로 나뉘어 저장되지만, 받는 사람에게는 한 번 보낸 것이다.
 * 알림까지 열 번 울리면 화면에 사진 묶음 하나로 보이는 것과 앞뒤가 맞지 않고, 27명 방이면
 * FCM 호출이 270번으로 불어난다. 마지막 장이 올라온 시점에 "사진 10장" 한 번만 보낸다.
 *
 * 보내는 쪽이 "몇 장 중 몇 번째"를 알려줄 때만 동작한다. 알려주지 않으면(구버전 앱, 한 장짜리 전송)
 * 호출하는 쪽에서 지금까지처럼 메시지마다 알림을 보낸다.
 *
 * 서버가 한 대라 상태를 메모리에 든다. 여러 대로 늘리면 이 상태를 Redis로 옮겨야 한다
 * (그전까지는 묶음이 인스턴스별로 나뉘어 알림이 두어 번 갈 수 있다).
 */
@Slf4j
public class ChatMessageBatchCollector {

    /** 묶음이 다 모였을 때 실제로 알림을 보내는 쪽 */
    @FunctionalInterface
    public interface Ready {
        void send(Long roomId, Long lastMessageId, int count);
    }

    private static final class PendingBatch {
        private final Long roomId;
        private Long lastMessageId;
        private int count;
        private ScheduledFuture<?> timeout;

        private PendingBatch(Long roomId) {
            this.roomId = roomId;
        }
    }

    private final Ready ready;
    private final long waitMillis;
    private final ScheduledExecutorService timer;
    private final Map<String, PendingBatch> pending = new ConcurrentHashMap<>();

    /**
     * @param waitMillis 마지막 장을 못 기다릴 때의 안전망 시간. 중간 한 장이 실패해 묶음이 영영
     *                   안 채워지면 알림이 통째로 사라지므로, 이 시간이 지나면 그때까지 모인 수로 보낸다.
     */
    public ChatMessageBatchCollector(Ready ready, long waitMillis) {
        this.ready = ready;
        this.waitMillis = waitMillis;
        this.timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chat-push-batch");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 보내는 쪽이 "몇 장 중 몇 번째"를 알려준 전송인지 */
    public static boolean isBatched(String batchId, Integer batchSize) {
        return batchId != null && !batchId.isBlank() && batchSize != null && batchSize > 1;
    }

    /** 묶음의 한 장이 도착했다. 마지막 장이면 바로 보내고, 아니면 다음 장을 기다린다. */
    public void collect(Long roomId, Long messageId, String batchId, int batchSize) {
        String key = roomId + ":" + batchId;
        boolean[] complete = {false};

        pending.compute(key, (k, existing) -> {
            PendingBatch batch = (existing != null) ? existing : new PendingBatch(roomId);
            batch.count += 1;
            batch.lastMessageId = messageId;

            if (batch.count >= batchSize) {
                complete[0] = true;
            } else if (batch.timeout == null) {
                batch.timeout = timer.schedule(() -> flush(key), waitMillis, TimeUnit.MILLISECONDS);
            }
            return batch;
        });

        if (complete[0]) {
            flush(key);
        }
    }

    /** 모아둔 묶음을 알림 한 건으로 내보낸다 */
    private void flush(String key) {
        PendingBatch batch = pending.remove(key);
        if (batch == null || batch.lastMessageId == null) {
            return;
        }
        if (batch.timeout != null) {
            batch.timeout.cancel(false);
        }
        try {
            ready.send(batch.roomId, batch.lastMessageId, batch.count);
        } catch (Exception e) {
            log.error("[Chat Batch] 묶음 알림 전송 실패: key={}, error={}", key, e.getMessage());
        }
    }

    public void shutdown() {
        timer.shutdownNow();
    }
}
