package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

/**
 * 접속 상태 — WebSocket으로 등록/해제하고, 처음 화면을 그릴 때는 REST로 현재 접속자를 받아간다.
 */
@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
@Slf4j
public class PresenceController {

    private final PresenceService presenceService;

    /**
     * 접속 알림 — 클라이언트가 연결 직후 자기 정보를 보낸다.
     * (STOMP CONNECT 시점에는 어느 기관 사람인지 알 수 없어 별도 메시지로 받는다)
     */
    @MessageMapping("/presence/join")
    public void join(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        try {
            String sessionId = headerAccessor.getSessionId();
            String userId = payload.get("userId") == null ? null : String.valueOf(payload.get("userId"));
            Long companyId = payload.get("companyId") == null ? null : Long.valueOf(String.valueOf(payload.get("companyId")));
            presenceService.join(sessionId, userId, companyId);
        } catch (Exception e) {
            log.warn("[Presence] 접속 등록 실패: {}", e.getMessage());
        }
    }

    /** 연결이 끊기면(탭 닫기·네트워크 끊김 포함) 자동으로 오프라인 처리 */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presenceService.leave(event.getSessionId());
    }

    /** 현재 접속 중인 사람 목록 (첫 렌더용) */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getOnlineUsers(@RequestParam Long companyId) {
        return ResponseEntity.ok(Map.of("onlineUserIds", presenceService.getOnlineUserIds(companyId)));
    }
}
