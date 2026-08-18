package com.silverithm.vehicleplacementsystem.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;



import java.util.Collection;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemMonitoringService implements HealthIndicator {

    private final SlackService slackService;
    private final MeterRegistry meterRegistry;
    
    @Value("${monitoring.memory.threshold:80}")
    private double memoryThreshold;
    
    @Value("${monitoring.error.rate.threshold:5}")
    private double errorRateThreshold;
    
    @Value("${monitoring.response.time.threshold:2}")
    private double responseTimeThreshold;
    
    @Value("${monitoring.enabled:true}")
    private boolean monitoringEnabled;

    // 블루그린 배포 색 (docker-compose에서 DEPLOY_COLOR 환경변수로 주입)
    @Value("${DEPLOY_COLOR:unknown}")
    private String deployColor;
    
    private boolean lastMemoryAlertSent = false;
    private boolean lastErrorRateAlertSent = false;
    private boolean lastResponseTimeAlertSent = false;
    
    /**
     * 시스템 상태 체크 (5분마다)
     */
    @Scheduled(fixedRate = 300000) // 5분
    public void checkSystemHealth() {
        if (!monitoringEnabled) {
            return;
        }
        
        log.debug("시스템 상태 체크 시작");
        
        try {
            // 메모리 사용량 체크
            checkMemoryUsage();
            
            // 에러율 체크
            checkErrorRate();
            
            // 응답시간 체크
            checkResponseTime();
            
            log.debug("시스템 상태 체크 완료");
        } catch (Exception e) {
            log.error("시스템 상태 체크 중 오류 발생: {}", e.getMessage(), e);
            slackService.sendSystemAlert("시스템 모니터링 오류", 
                    "시스템 상태 체크 중 오류가 발생했습니다: " + e.getMessage(), 
                    "WARNING");
        }
    }
    
    /**
     * 메모리 사용량 체크
     */
    private void checkMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        
        if (max > 0) {
            double usagePercent = (double) used / max * 100;
            
            if (usagePercent > memoryThreshold) {
                if (!lastMemoryAlertSent) {
                    slackService.sendHighMemoryUsageAlert(usagePercent, "JVM Heap");
                    lastMemoryAlertSent = true;
                }
            } else if (usagePercent < memoryThreshold - 10) {
                // 메모리 사용량이 정상으로 돌아왔을 때 플래그 리셋
                lastMemoryAlertSent = false;
            }
            
            log.debug("메모리 사용량: {:.2f}%", usagePercent);
        }
    }
    
    /**
     * 에러율 체크
     */
    private void checkErrorRate() {
        try {
            // http.server.requests는 (uri, method, status ...) 조합마다 타이머가 따로 있다.
            // .timer()는 그중 하나만 집어오므로, 분모를 그걸로 잡으면 '전체 요청'이 아니라
            // '어쩌다 걸린 한 조합'이 되어 에러율이 100%로 튄다(실제로 오탐이 왔다).
            // 조합을 모두 더해야 진짜 비율이 된다.
            Collection<Timer> timers = meterRegistry.get("http.server.requests").timers();

            long totalRequests = 0;
            long errorRequests = 0;
            for (Timer timer : timers) {
                long count = timer.count();
                totalRequests += count;
                String status = timer.getId().getTag("status");
                // 5xx만 서버 잘못이다. 401·403·404는 잘못된 요청이지 장애가 아니다.
                if (status != null && status.startsWith("5")) {
                    errorRequests += count;
                }
            }

            // 누적값이라 재시작 이후 전체 평균이다 — '최근 5분'이라 부르지 않는다
            if (totalRequests > 0) {
                double errorRate = ((double) errorRequests / totalRequests) * 100;

                if (errorRate > errorRateThreshold) {
                    if (!lastErrorRateAlertSent) {
                        slackService.sendHighErrorRateAlert(errorRate, "기동 후 누적");
                        lastErrorRateAlertSent = true;
                    }
                } else if (errorRate < errorRateThreshold - 2) {
                    // 에러율이 정상으로 돌아왔을 때 플래그 리셋
                    lastErrorRateAlertSent = false;
                }

                log.debug("에러율: {}% ({}/{})", String.format("%.2f", errorRate), errorRequests, totalRequests);
            }
        } catch (Exception e) {
            log.debug("에러율 체크 중 메트릭을 찾을 수 없음: {}", e.getMessage());
        }
    }
    
    /**
     * 응답시간 체크
     */
    private void checkResponseTime() {
        try {
            Timer timer = meterRegistry.get("http.server.requests").timer();
            double averageResponseTime = timer.mean(TimeUnit.SECONDS);
            
            if (averageResponseTime > responseTimeThreshold) {
                if (!lastResponseTimeAlertSent) {
                    slackService.sendHighResponseTimeAlert(averageResponseTime, "전체 API");
                    lastResponseTimeAlertSent = true;
                }
            } else if (averageResponseTime < responseTimeThreshold - 0.5) {
                // 응답시간이 정상으로 돌아왔을 때 플래그 리셋
                lastResponseTimeAlertSent = false;
            }
            
            log.debug("평균 응답시간: {:.2f}초", averageResponseTime);
        } catch (Exception e) {
            log.debug("응답시간 체크 중 메트릭을 찾을 수 없음: {}", e.getMessage());
        }
    }
    
    /**
     * 서버 시작 알림
     */
    @Scheduled(initialDelay = 30000, fixedDelay = Long.MAX_VALUE) // 30초 후 한 번만 실행
    public void sendServerStartNotification() {
        if (!monitoringEnabled) {
            return;
        }
        
        slackService.sendSystemAlert("서버 시작",
                "Silverithm 서버(" + deployColor + ")가 정상적으로 시작되었습니다.",
                "INFO");
    }
    
    /**
     * 주간 시스템 상태 리포트 (매주 월요일 오전 9시)
     */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void sendWeeklyReport() {
        if (!monitoringEnabled) {
            return;
        }
        
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeHours = uptime / (1000 * 60 * 60);
        
        double memoryUsage = 0;
        if (heapUsage.getMax() > 0) {
            memoryUsage = (double) heapUsage.getUsed() / heapUsage.getMax() * 100;
        }
        
        String report = String.format(
                "📊 **주간 시스템 상태 리포트**\n" +
                "• 서버 가동시간: %d시간\n" +
                "• 현재 메모리 사용량: %.2f%%\n" +
                "• 보고서 생성일: %s\n" +
                "• 상태: 정상 운영중 ✅",
                uptimeHours, memoryUsage, LocalDateTime.now().toString());
        
        slackService.sendSystemAlert("주간 시스템 리포트", report, "INFO");
    }
    

    
    /**
     * Health Check 엔드포인트
     */
    @Override
    public Health health() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        double usagePercent = max > 0 ? (double) used / max * 100 : 0;
        
        Health.Builder builder = usagePercent > 90 ? Health.down() : Health.up();
        
        return builder
                .withDetail("memory.used", used)
                .withDetail("memory.max", max)
                .withDetail("memory.usage.percent", String.format("%.2f%%", usagePercent))
                .withDetail("timestamp", LocalDateTime.now().toString())
                .build();
    }
} 