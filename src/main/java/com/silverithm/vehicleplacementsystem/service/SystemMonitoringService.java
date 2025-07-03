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
            // 최근 5분간 총 요청 수
            long totalRequests = meterRegistry.get("http.server.requests")
                    .timer()
                    .count();
            
            // 최근 5분간 5xx 에러 수
            long errorRequests = meterRegistry.get("http.server.requests")
                    .tags("status", "500")
                    .timer()
                    .count();
            
            if (totalRequests > 0) {
                double errorRate = ((double) errorRequests / totalRequests) * 100;
                
                if (errorRate > errorRateThreshold) {
                    if (!lastErrorRateAlertSent) {
                        slackService.sendHighErrorRateAlert(errorRate, "최근 5분간");
                        lastErrorRateAlertSent = true;
                    }
                } else if (errorRate < errorRateThreshold - 2) {
                    // 에러율이 정상으로 돌아왔을 때 플래그 리셋
                    lastErrorRateAlertSent = false;
                }
                
                log.debug("에러율: {:.2f}%", errorRate);
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
                "Silverithm 서버가 정상적으로 시작되었습니다.", 
                "INFO");
    }
    
    /**
     * 주간 시스템 상태 리포트 (매주 월요일 오전 9시)
     */
    @Scheduled(cron = "0 0 9 * * MON")
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