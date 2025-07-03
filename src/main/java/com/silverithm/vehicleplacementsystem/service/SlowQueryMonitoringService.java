package com.silverithm.vehicleplacementsystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.HashMap;

@Service
public class SlowQueryMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(SlowQueryMonitoringService.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private SlackService slackService;

    @Async
    public void logSlowQuery(String query, long executionTime, String additionalInfo) {
        try {
            String apiEndpoint = getCurrentApiEndpoint();
            String httpMethod = getCurrentHttpMethod();
            String userAgent = getCurrentUserAgent();
            
            ObjectNode logData = objectMapper.createObjectNode();
            logData.put("type", "SLOW_QUERY");
            logData.put("query", query);
            logData.put("execution_time_ms", executionTime);
            logData.put("api_endpoint", apiEndpoint);
            logData.put("http_method", httpMethod);
            logData.put("user_agent", userAgent);
            logData.put("additional_info", additionalInfo);
            logData.put("timestamp", System.currentTimeMillis());
            
            // 구조화된 로그 출력 (Loki에서 파싱 가능)
            logger.warn("SLOW_QUERY_DETECTED: {}", logData.toString());
            
            // 모든 슬로우 쿼리에 대해 Slack 알림 (500ms 이상)
            if (executionTime > 500) { // 500ms 이상
                sendSlackAlert(query, executionTime, apiEndpoint, httpMethod);
            }
            
        } catch (Exception e) {
            logger.error("슬로우 쿼리 로깅 중 오류 발생", e);
        }
    }
    
    private String getCurrentApiEndpoint() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getRequestURI();
            }
        } catch (Exception e) {
            logger.debug("API 엔드포인트 정보 추출 실패", e);
        }
        return "UNKNOWN";
    }
    
    private String getCurrentHttpMethod() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getMethod();
            }
        } catch (Exception e) {
            logger.debug("HTTP 메서드 정보 추출 실패", e);
        }
        return "UNKNOWN";
    }
    
    private String getCurrentUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            logger.debug("User-Agent 정보 추출 실패", e);
        }
        return "UNKNOWN";
    }
    
    private void sendSlackAlert(String query, long executionTime, String apiEndpoint, String httpMethod) {
        try {
            String severity = executionTime > 3000 ? "🚨 *매우 심각한" : executionTime > 1000 ? "⚠️ *심각한" : "🐌 *슬로우";
            String alertLevel = executionTime > 3000 ? "CRITICAL" : executionTime > 1000 ? "WARNING" : "INFO";
            
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("text", String.format("%s 쿼리 감지*\n" +
                "• *실행 시간*: %.2f초\n" +
                "• *API 엔드포인트*: `%s %s`\n" +
                "• *쿼리*: ```%s```\n" +
                "• *시각*: %s", 
                severity,
                executionTime / 1000.0, 
                httpMethod, 
                apiEndpoint, 
                query.length() > 200 ? query.substring(0, 200) + "..." : query,
                new java.util.Date()));
            
            slackService.sendSystemAlert("슬로우 쿼리 감지", alertData.get("text").toString(), alertLevel);
        } catch (Exception e) {
            logger.error("슬로우 쿼리 Slack 알림 전송 실패", e);
        }
    }
} 