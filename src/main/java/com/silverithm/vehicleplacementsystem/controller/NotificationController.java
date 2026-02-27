package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.NotificationDTO;
import com.silverithm.vehicleplacementsystem.entity.Notification;
import com.silverithm.vehicleplacementsystem.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Validated
public class NotificationController {
    
    private final NotificationService notificationService;
    
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendNotification(
            @Valid @RequestBody FCMNotificationRequestDTO requestDTO) {
        
        try {
            log.info("[Notification API] 알림 전송 요청: {}", requestDTO.getTitle());
            
            NotificationDTO result = notificationService.sendAndSaveNotification(requestDTO);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "notification", result
                    ));
                    
        } catch (Exception e) {
            log.error("[Notification API] 알림 전송 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "알림 전송 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, List<NotificationDTO>>> getNotificationsByUserId(
            @PathVariable String userId) {
        
        try {
            log.info("[Notification API] 사용자 알림 조회: userId={}", userId);
            
            List<NotificationDTO> notifications = notificationService.getNotificationsByUserId(userId);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("notifications", notifications));
                    
        } catch (Exception e) {
            log.error("[Notification API] 사용자 알림 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    @GetMapping("/username/{userName}")
    public ResponseEntity<Map<String, List<NotificationDTO>>> getNotificationsByUserName(
            @PathVariable String userName) {
        
        try {
            log.info("[Notification API] 사용자 알림 조회: userName={}", userName);
            
            List<NotificationDTO> notifications = notificationService.getNotificationsByUserName(userName);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("notifications", notifications));
                    
        } catch (Exception e) {
            log.error("[Notification API] 사용자 알림 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    @GetMapping("/type/{type}")
    public ResponseEntity<?> getNotificationsByType(
            @PathVariable String type) {
        
        try {
            log.info("[Notification API] 타입별 알림 조회: type={}", type);
            
            Notification.NotificationType notificationType = 
                    Notification.NotificationType.valueOf(type.toUpperCase());
            
            List<NotificationDTO> notifications = notificationService.getNotificationsByType(notificationType);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("notifications", notifications));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Notification API] 잘못된 알림 타입: {}", type);
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "잘못된 알림 타입입니다: " + type));
        } catch (Exception e) {
            log.error("[Notification API] 타입별 알림 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long id) {
        try {
            log.info("[Notification API] 알림 읽음 처리: id={}", id);

            NotificationDTO result = notificationService.markAsRead(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "notification", result
                    ));

        } catch (IllegalArgumentException e) {
            log.error("[Notification API] 알림 읽음 처리 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Notification API] 알림 읽음 처리 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "알림 읽음 처리 중 오류가 발생했습니다"));
        }
    }

    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(@PathVariable String userId) {
        try {
            log.info("[Notification API] 전체 알림 읽음 처리: userId={}", userId);

            notificationService.markAllAsRead(userId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "모든 알림이 읽음 처리되었습니다"
                    ));

        } catch (Exception e) {
            log.error("[Notification API] 전체 알림 읽음 처리 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "전체 알림 읽음 처리 중 오류가 발생했습니다"));
        }
    }

    @GetMapping("/failed")
    public ResponseEntity<Map<String, List<NotificationDTO>>> getFailedNotifications() {
        try {
            log.info("[Notification API] 실패한 알림 조회");
            
            List<NotificationDTO> notifications = notificationService.getFailedNotifications();
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("notifications", notifications));
                    
        } catch (Exception e) {
            log.error("[Notification API] 실패한 알림 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    @PostMapping("/retry-failed")
    public ResponseEntity<Map<String, String>> retryFailedNotifications() {
        try {
            log.info("[Notification API] 실패한 알림 재전송 요청");
            
            notificationService.retryFailedNotifications();
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("message", "실패한 알림 재전송이 완료되었습니다"));
                    
        } catch (Exception e) {
            log.error("[Notification API] 실패한 알림 재전송 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "재전송 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .headers(getCorsHeaders())
                .build();
    }
    
    private HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        return headers;
    }
} 