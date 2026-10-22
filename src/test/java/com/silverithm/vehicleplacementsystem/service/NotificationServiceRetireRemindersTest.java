package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.repository.NotificationRepository;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 지난 리마인더 정리 — 서비스는 저장소의 벌크 UPDATE에 종류·기준 시각을 그대로 넘기고, 독립 트랜잭션으로 돈다. */
class NotificationServiceRetireRemindersTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repository, mock(FCMService.class));

    @Test
    @DisplayName("종류와 기준 시각을 저장소에 넘기고, 읽음 시각은 지금으로 찍고, 건수를 돌려준다")
    void 저장소에위임() {
        LocalDateTime before = LocalDateTime.of(2026, 10, 22, 0, 0);
        when(repository.markReadByRelatedEntityTypeCreatedBefore(eq("vacation_adjustment"), eq(before), any()))
                .thenReturn(4);
        LocalDateTime started = LocalDateTime.now();

        int retired = service.retireStaleReminders("vacation_adjustment", before);

        assertThat(retired).isEqualTo(4);
        ArgumentCaptor<LocalDateTime> readAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).markReadByRelatedEntityTypeCreatedBefore(eq("vacation_adjustment"), eq(before),
                readAt.capture());
        assertThat(readAt.getValue()).isAfterOrEqualTo(started);
    }

    @Test
    @DisplayName("독립 트랜잭션(REQUIRES_NEW)이다 — 호출하는 스케줄러가 readOnly 트랜잭션이라 합류하면 벌크 UPDATE가 막힌다")
    void 독립트랜잭션() throws Exception {
        Method method = NotificationService.class.getMethod("retireStaleReminders", String.class, LocalDateTime.class);

        Transactional tx = method.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(tx.readOnly()).isFalse();
    }
}
