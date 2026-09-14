package com.silverithm.vehicleplacementsystem.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 메시지 전송이 어떻게 끝났는지 세는 계수기. Prometheus로 나간다({@code chat_message_send_total}).
 *
 * <p>"메시지가 안 갔다"는 제보가 왔을 때 로그를 뒤지는 대신 그래프로 먼저 알기 위한 것이다.
 * 결과(result) 라벨: stored(새로 저장) · duplicate(재전송을 받아 기존 것을 돌려줌) ·
 * deadlock_retry(교착으로 다시 시도) · failed(끝내 실패).
 */
@Component
public class ChatSendMetrics {

    private final Counter stored;
    private final Counter duplicate;
    private final Counter deadlockRetry;
    private final Counter failed;

    public ChatSendMetrics(MeterRegistry registry) {
        this.stored = counter(registry, "stored");
        this.duplicate = counter(registry, "duplicate");
        this.deadlockRetry = counter(registry, "deadlock_retry");
        this.failed = counter(registry, "failed");
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("chat.message.send")
                .description("채팅 메시지 전송 결과")
                .tag("result", result)
                .register(registry);
    }

    public void stored() { stored.increment(); }
    public void duplicate() { duplicate.increment(); }
    public void deadlockRetry() { deadlockRetry.increment(); }
    public void failed() { failed.increment(); }
}
