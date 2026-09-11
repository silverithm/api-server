package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.jwt.CarevPrincipal;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 토큰에 적힌 사람이 누구인지 찾는다.
 *
 * **관리자와 직원은 서로 다른 표에 산다.** 토큰 검증이 관리자 표(app_user)만 뒤지는 바람에
 * 직원이 낸 토큰은 늘 "존재하지 않는 사용자"가 되어 무효로 판정됐다. 앱은 그 답을 받고
 * 토큰을 갱신한 뒤 다시 물었다가 또 무효를 듣고 로그인 화면으로 돌아갔다 —
 * 자동로그인이 직원에게만 계속 풀리던 까닭이다.
 * (운영 로그 48시간: 앱의 토큰 검증 561번 무효 / 44번 유효, 갱신은 981번 모두 성공.)
 */
@Component
@RequiredArgsConstructor
public class TokenIdentityResolver {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    /** 토큰이 가리키는 사람. 관리자든 직원이든 같은 모양으로 돌려준다. */
    public record TokenIdentity(String email, String displayName, Long id, boolean admin) {
    }

    /**
     * [principalType]/[principalId]는 요즘 토큰에만 있는 클레임이다. 예전에 발급돼 아직 살아 있는
     * 토큰에는 없으므로 [subject](아이디/이메일)로도 찾을 수 있어야 한다.
     */
    public Optional<TokenIdentity> resolve(String principalType, Long principalId, String subject) {
        if (CarevPrincipal.TYPE_MEMBER.equals(principalType)) {
            return findMember(principalId, subject);
        }
        if (CarevPrincipal.TYPE_ADMIN.equals(principalType)) {
            return findAdmin(subject);
        }
        // 클레임이 없는 옛 토큰 — 관리자 표를 먼저 보고, 없으면 직원 표를 본다
        Optional<TokenIdentity> admin = findAdmin(subject);
        return admin.isPresent() ? admin : findMember(null, subject);
    }

    private Optional<TokenIdentity> findAdmin(String subject) {
        if (subject == null || subject.isBlank()) return Optional.empty();
        return userRepository.findActiveByEmail(subject)
                .map(user -> new TokenIdentity(user.getEmail(), user.getUsername(), user.getId(), true));
    }

    private Optional<TokenIdentity> findMember(Long principalId, String subject) {
        Optional<Member> member = Optional.empty();
        if (principalId != null) {
            member = memberRepository.findById(principalId);
        }
        if (member.isEmpty() && subject != null && !subject.isBlank()) {
            member = memberRepository.findByUsername(subject);
        }
        // 탈퇴한 직원의 토큰은 살려 두지 않는다
        return member
                .filter(m -> m.getStatus() != Member.MemberStatus.DELETED)
                .map(m -> new TokenIdentity(m.getEmail(), m.getName(), m.getId(), false));
    }
}
