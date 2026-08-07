package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.repository.ExternalNoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 노인장기요양보험(longtermcare.or.kr) 공지 - 일일 자동 수집 스케줄러.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalNoticeScheduler {

    private final ExternalNoticeCrawlerService externalNoticeCrawlerService;
    private final ExternalNoticeRepository externalNoticeRepository;

    /** 서버 기동 직후: 저장된 공지가 없으면 1회 수집 (기동 자체를 막지 않도록 실패는 서비스 내부에서 처리) */
    @EventListener(ApplicationReadyEvent.class)
    public void collectOnStartupIfEmpty() {
        try {
            if (externalNoticeRepository.count() == 0) {
                log.info("[ExternalNotice] 저장된 공지가 없어 초기 수집을 시작합니다");
                externalNoticeCrawlerService.collect();
            }
        } catch (Exception e) {
            log.error("[ExternalNotice] 초기 수집 실패 (서비스 기동에는 영향 없음)", e);
        }
    }

    /** 매일 07시 (KST) 정기 수집 */
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    public void collectScheduled() {
        externalNoticeCrawlerService.collect();
    }
}
