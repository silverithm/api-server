package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 데모 테넌트를 매일 새벽 정리한다. (SubscriptionScheduler 6시와 겹치지 않게 5시 30분)
 * 회사별 개별 트랜잭션으로 삭제해 한 건이 실패해도 나머지는 계속 진행하고,
 * 실패 건은 다음 날 재시도된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoCleanupScheduler {

    private final CompanyRepository companyRepository;
    private final DemoTeardownService demoTeardownService;

    @Scheduled(cron = "${demo.cleanup.cron:0 30 5 * * *}", zone = "Asia/Seoul")
    public void cleanupExpiredDemoTenants() {
        List<Company> expired = companyRepository.findByIsDemoTrueAndDemoExpiresAtBefore(LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }

        log.info("[Demo] 만료 데모 테넌트 정리 시작: {}건", expired.size());
        int success = 0;
        for (Company company : expired) {
            try {
                demoTeardownService.deleteDemoTenant(company);
                success++;
            } catch (Exception e) {
                log.error("[Demo] 데모 테넌트 삭제 실패 (다음 배치에서 재시도): companyId={}", company.getId(), e);
            }
        }
        log.info("[Demo] 만료 데모 테넌트 정리 완료: {}/{}건 성공", success, expired.size());
    }
}
