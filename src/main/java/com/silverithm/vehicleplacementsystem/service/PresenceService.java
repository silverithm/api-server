package com.silverithm.vehicleplacementsystem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 접속 중인 사람 추적.
 *
 * 채팅용 WebSocket(STOMP) 연결이 붙어 있는 동안 '온라인'으로 본다.
 * 상태를 메모리에만 두는 것은 의도적이다 — 서버가 재시작되면 모든 연결이 끊기므로
 * 그때는 전원 오프라인으로 시작하는 게 실제와 맞다. (DB에 남기면 유령 접속이 생긴다)
 *
 * 한 사람이 웹·앱·여러 탭에서 동시에 붙을 수 있어 세션 수를 세고,
 * 마지막 세션이 끊어질 때만 오프라인으로 알린다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;

    /** sessionId → 누가 어느 기관으로 붙었는지 */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** companyId → (userId → 열려 있는 세션 수) */
    private final Map<Long, Map<String, Integer>> onlineByCompany = new ConcurrentHashMap<>();

    private record Session(String userId, Long companyId) {}

    /**
     * 접속 등록. 같은 사람이 이미 온라인이면 세션 수만 늘리고 알리지 않는다.
     */
    public void join(String sessionId, String userId, Long companyId) {
        if (sessionId == null || userId == null || companyId == null) {
            return;
        }
        // 같은 세션이 두 번 들어오면(재구독 등) 중복 카운트하지 않는다
        if (sessions.containsKey(sessionId)) {
            return;
        }
        sessions.put(sessionId, new Session(userId, companyId));

        Map<String, Integer> users = onlineByCompany.computeIfAbsent(companyId, k -> new ConcurrentHashMap<>());
        int before = users.getOrDefault(userId, 0);
        users.put(userId, before + 1);

        if (before == 0) {
            log.info("[Presence] 온라인: companyId={}, userId={}", companyId, userId);
            broadcast(companyId, userId, true);
        }
    }

    /**
     * 연결 해제. 그 사람의 마지막 세션이었을 때만 오프라인으로 알린다.
     */
    public void leave(String sessionId) {
        Session session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }

        Map<String, Integer> users = onlineByCompany.get(session.companyId());
        if (users == null) {
            return;
        }

        int remaining = users.getOrDefault(session.userId(), 1) - 1;
        if (remaining > 0) {
            users.put(session.userId(), remaining);
            return;
        }

        users.remove(session.userId());
        log.info("[Presence] 오프라인: companyId={}, userId={}", session.companyId(), session.userId());
        broadcast(session.companyId(), session.userId(), false);
    }

    /** 지금 접속 중인 사람들의 userId */
    public Set<String> getOnlineUserIds(Long companyId) {
        Map<String, Integer> users = onlineByCompany.get(companyId);
        return users == null ? Set.of() : Collections.unmodifiableSet(users.keySet());
    }

    private void broadcast(Long companyId, String userId, boolean online) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/presence/" + companyId,
                    Map.of("userId", userId, "online", online));
        } catch (Exception e) {
            // 알림 실패가 접속 추적 자체를 막아서는 안 된다
            log.warn("[Presence] 상태 전파 실패: {}", e.getMessage());
        }
    }
}
