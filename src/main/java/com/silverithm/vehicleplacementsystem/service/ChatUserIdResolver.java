package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 옛 클라이언트가 접두사 없이 보내는 관리자 채팅 식별자를 'admin_&lt;id&gt;'로 맞춘다.
 *
 * V1.65에서 채팅 참가자·메시지의 관리자 식별자에 'admin_' 접두사를 붙였다. 웹은 같이 고쳤지만
 * 앱은 스토어 배포가 있어야 반영되고, 이미 깔린 앱은 계속 원시 숫자를 보낸다. 그러면 관리자가
 * 자기 방을 하나도 못 보고(참가자 행이 admin_N으로 바뀌었으므로), id가 겹치는 직원이 있으면
 * 그 사람 방이 보인다. 그래서 서버에서 받아 넘긴다.
 *
 * 판단은 **로그인한 사람**을 기준으로만 한다 — 요청 본문의 값은 믿지 않는다.
 * 관리자 계정(app_user)으로 인증된 요청이고, 보낸 값이 그 계정의 id와 같을 때만 접두사를 붙인다.
 * 직원(members) 토큰이거나 남의 id를 보냈으면 손대지 않는다.
 *
 * 앱이 새 규약으로 올라가면(이미 'admin_'을 붙여 보내면) 이 클래스는 그대로 통과시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatUserIdResolver {

    /** ChatService.ADMIN_ID_PREFIX와 같은 값 */
    private static final String ADMIN_ID_PREFIX = "admin_";

    private final UserRepository userRepository;

    public String resolve(String userId) {
        return resolve(userId, callerEmail());
    }

    /**
     * 웹소켓용 — STOMP 메시지 처리 스레드에는 SecurityContext가 없어서
     * 연결할 때 붙여둔 Principal의 이메일을 직접 받는다.
     */
    public String resolve(String userId, String callerEmail) {
        if (userId == null || userId.isBlank()) {
            return userId;
        }

        String trimmed = userId.trim();
        if (trimmed.startsWith(ADMIN_ID_PREFIX) || !trimmed.matches("\\d+")) {
            return userId;
        }

        if (callerEmail == null || callerEmail.isBlank() || "anonymousUser".equals(callerEmail)) {
            return userId;
        }

        AppUser caller = userRepository.findActiveByEmail(callerEmail).orElse(null);
        if (caller == null || !String.valueOf(caller.getId()).equals(trimmed)) {
            return userId;
        }

        log.debug("[Chat] 옛 앱이 보낸 관리자 식별자 보정: {} -> {}{}", trimmed, ADMIN_ID_PREFIX, trimmed);
        return ADMIN_ID_PREFIX + trimmed;
    }

    /** 지금 요청을 보낸 사람의 로그인 이메일 (없으면 null) */
    private String callerEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }
}
