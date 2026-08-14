package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.jwt.CarevPrincipal;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * "이 요청을 보낸 사람"의 채팅 식별자를 토큰에서 정한다.
 *
 * 예전에는 클라이언트가 `?userId=3`처럼 자기가 누구인지를 서버에 알려줬다. 두 가지가 잘못됐다.
 *   1. 남의 id를 적어 보내면 남의 채팅방 목록이 그대로 나왔다.
 *   2. 관리자 식별자 규약이 'admin_&lt;id&gt;'로 바뀌자(V1.65) 옛 앱이 통째로 어긋났다.
 * 그래서 채팅에서 '나'를 가리키는 값은 더 이상 요청에서 받지 않고 여기서 만든다.
 * (참가자 초대·추방처럼 '남'을 가리키는 값은 그대로 요청에서 받는다)
 *
 * 토큰에 신원 클레임이 있으면 그것만으로 끝나고, 클레임이 없는 옛 토큰이면 이름으로 DB를 찾는다.
 * 직원 로그인은 members.username이, 관리자 로그인은 app_user.email이 토큰 subject다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatCallerResolver {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    /** REST 요청용 — SecurityContext에서 꺼낸다 */
    public String currentChatUserId() {
        return chatUserIdOf(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * 웹소켓용 — STOMP 처리 스레드에는 SecurityContext가 없다.
     * 연결할 때 붙여둔 Principal이 곧 Authentication이다.
     */
    public String chatUserIdOf(Principal principal) {
        return principal instanceof Authentication authentication
                ? chatUserIdOf(authentication)
                : null;
    }

    private String chatUserIdOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication.getPrincipal() instanceof CarevPrincipal principal && principal.hasIdentity()) {
            return principal.isAdminAccount()
                    ? ChatService.toAdminChatUserId(principal.getPrincipalId())
                    : String.valueOf(principal.getPrincipalId());
        }

        // 신원 클레임이 없는 옛 토큰 — 이름으로 찾는다 (재로그인하면 위 경로로 간다)
        return chatUserIdByName(authentication.getName());
    }

    private String chatUserIdByName(String name) {
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }

        Member member = memberRepository.findByUsername(name).orElse(null);
        if (member != null) {
            return String.valueOf(member.getId());
        }

        AppUser admin = userRepository.findActiveByEmail(name).orElse(null);
        if (admin != null) {
            return ChatService.toAdminChatUserId(admin.getId());
        }

        log.warn("[Chat] 호출자를 찾지 못했습니다 — 요청이 보낸 값을 그대로 씁니다");
        return null;
    }

    /**
     * 호출자를 알아내면 그 값을, 못 알아내면 클라이언트가 보낸 값을 쓴다.
     * 폴백을 두는 이유: 신원을 못 찾는 예외 상황에서 채팅이 통째로 멈추는 것보다는
     * 예전 동작으로 굴러가는 편이 낫다.
     */
    public String resolveSelf(String clientProvided) {
        String resolved = currentChatUserId();
        return resolved != null ? resolved : clientProvided;
    }

    public String resolveSelf(String clientProvided, Principal principal) {
        String resolved = chatUserIdOf(principal);
        return resolved != null ? resolved : clientProvided;
    }
}
