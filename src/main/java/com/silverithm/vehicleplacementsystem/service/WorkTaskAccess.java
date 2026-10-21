package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업무진행도를 부른 사람 — 채팅 식별자·이름·소속 기관·관리자 여부.
 *
 * <p>요청 본문의 사람 값은 믿지 않는다. 기관은 {@link CallerCompanyResolver}, 식별자는
 * {@link ChatCallerResolver}가 토큰으로 정한다 — 앱·웹 어디서 불러도 같은 사람이 된다.
 */
@Component
@RequiredArgsConstructor
public class WorkTaskAccess {

    private final ChatCallerResolver chatCallerResolver;
    private final CallerCompanyResolver callerCompanyResolver;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    /**
     * @param personId 채팅 식별자 — 직원 "12", 관리자 "admin_3"
     * @param admin 기관 관리자(가입 계정 또는 ADMIN 역할 직원)
     */
    public record Caller(String personId, String name, Company company, boolean admin) {
        public Long companyId() {
            return company.getId();
        }
    }

    @Transactional(readOnly = true)
    public Caller requireCaller() {
        String username = authenticatedUsername();
        String personId = chatCallerResolver.currentChatUserId();
        if (username == null || personId == null) {
            throw new CustomException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED);
        }
        Company company = callerCompanyResolver.resolveCallerCompany(username)
                .orElseThrow(() -> new CustomException("소속 기관을 찾을 수 없습니다", HttpStatus.FORBIDDEN));

        Optional<Member> member = memberRepository.findByUsername(username);
        if (member.isPresent()) {
            return new Caller(personId, member.get().getName(), company,
                    member.get().getRole() == Member.Role.ADMIN);
        }
        String adminName = userRepository.findByEmail(username).map(AppUser::getUsername).orElse(null);
        return new Caller(personId, adminName, company, true);
    }

    private static String authenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? null : name;
    }
}
