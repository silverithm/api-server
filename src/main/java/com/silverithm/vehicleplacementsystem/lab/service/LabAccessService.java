package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import com.silverithm.vehicleplacementsystem.service.CallerCompanyResolver;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 실험실을 쓸 수 있는 요청자인지 판정한다 — 허용 기관 소속이면서 관리자일 때만.
 *
 * <p>가드 인터셉터와 모든 실험실 서비스가 같은 이 판정을 쓴다. 인터셉터만 믿으면 서비스가 다른 경로
 * (예: 나중에 붙는 컨트롤러)로 불렸을 때 뚫리므로, 서비스도 요청자의 기관을 직접 해석한다.
 * 클라이언트가 보낸 companyId는 어디에서도 받지 않는다.
 *
 * <p>관리자 판별은 어르신 주민번호 열람과 같은 규칙이다 — 관리자 계정(AppUser)이거나 직원 계정(Member)의
 * 역할이 ADMIN. 다만 소속 기관을 해석하는 {@link CallerCompanyResolver}가 Member를 먼저 보므로 관리자
 * 판별도 같은 순서로 맞춰, 두 표에 같은 식별자가 있어도 기관과 권한이 서로 다른 계정에서 오지 않게 한다.
 */
@Slf4j
@Service
public class LabAccessService {

    private static final String NOT_OPEN = "실험실은 아직 이 기관에 열려 있지 않습니다";

    /** 실험실이 열린 기관 ID들 */
    private final Set<Long> labCompanyIds;
    private final CallerCompanyResolver callerCompanyResolver;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    public LabAccessService(@Value("${lab.company-ids:4}") String companyIds,
                            CallerCompanyResolver callerCompanyResolver,
                            MemberRepository memberRepository,
                            UserRepository userRepository) {
        this.labCompanyIds = parseCompanyIds(companyIds);
        this.callerCompanyResolver = callerCompanyResolver;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /** 인증된 요청자가 실험실을 쓸 수 있는지. 인증이 없거나 기관·권한이 맞지 않으면 false. */
    public boolean isEnabled() {
        return resolveCaller().isPresent();
    }

    /** 실험실을 쓸 수 있는 요청자. 아니면 403 — 서비스가 기관 ID를 얻는 유일한 경로다. */
    public Caller requireCaller() {
        return resolveCaller().orElseThrow(() -> new CustomException(NOT_OPEN, HttpStatus.FORBIDDEN));
    }

    /** 요청자의 기관 ID (실험실 사용 가능 요청자만) */
    public Long requireCompanyId() {
        return requireCaller().companyId();
    }

    private Optional<Caller> resolveCaller() {
        String username = authenticatedUsername();
        if (username == null) {
            return Optional.empty();
        }
        Optional<Long> companyId = callerCompanyResolver.resolveCompanyId(username);
        if (companyId.isEmpty() || !labCompanyIds.contains(companyId.get())) {
            return Optional.empty();
        }
        if (!isAdmin(username)) {
            return Optional.empty();
        }
        return Optional.of(new Caller(username, companyId.get()));
    }

    private boolean isAdmin(String username) {
        Optional<Member> member = memberRepository.findByUsername(username);
        if (member.isPresent()) {
            return member.get().getRole() == Member.Role.ADMIN;
        }
        return userRepository.findByEmail(username).isPresent();
    }

    private String authenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        String name = authentication.getName();
        return (name == null || name.isBlank()) ? null : name;
    }

    /** 쉼표로 나열된 기관 ID. 잘못 적힌 조각은 건너뛴다 — 설정 오타 하나로 기동이 막히면 안 된다. */
    static Set<Long> parseCompanyIds(String raw) {
        Set<Long> ids = new HashSet<>();
        if (raw == null) {
            return ids;
        }
        Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(s -> {
            try {
                ids.add(Long.parseLong(s));
            } catch (NumberFormatException e) {
                log.warn("[Lab] lab.company-ids에 숫자가 아닌 값이 있어 건너뜁니다: {}", s);
            }
        });
        return ids;
    }

    /** 실험실을 쓸 수 있는 요청자 — 식별자(수정자 표기용)와 소속 기관 */
    public record Caller(String username, Long companyId) {
    }
}
