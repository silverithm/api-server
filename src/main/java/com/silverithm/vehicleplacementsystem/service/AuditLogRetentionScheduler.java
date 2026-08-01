package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.repository.AuditLogRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 감사 로그 보존 정책: 180일 지난 기록은 매일 새벽 정리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogRetentionScheduler {

    private static final int RETENTION_DAYS = 180;

    private final AuditLogRepository auditLogRepository;

    @Scheduled(cron = "0 50 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpiredAuditLogs() {
        int deleted = auditLogRepository.deleteOlderThan(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("[Audit] 보존기간({}일) 지난 감사 로그 {}건 정리", RETENTION_DAYS, deleted);
        }
    }
}
