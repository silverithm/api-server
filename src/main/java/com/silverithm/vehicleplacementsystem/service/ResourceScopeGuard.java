package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단건 리소스가 요청자의 소속 기관에 속하는지 검증한다.
 *
 * <p>{@code /{id}} 형태의 엔드포인트는 대부분 {@code findById(id)} 결과를 그대로 반환해,
 * ID를 순번으로 훑으면 타 기관의 결재 문서·채팅·공지가 열렸다(IDOR).
 * {@link CompanyScopeInterceptor}는 요청에 {@code companyId}가 실려 있을 때만 동작하므로
 * 이 경로는 막지 못한다.
 *
 * <p>요청자는 서비스 파라미터가 아니라 {@link SecurityContextHolder}에서 읽는다.
 * 컨트롤러·서비스 시그니처를 바꾸지 않고 로드 지점에 한 줄만 추가하기 위함이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceScopeGuard {

    private final CallerCompanyResolver callerCompanyResolver;

    /**
     * 리소스의 소속 기관이 요청자와 같은지 확인한다.
     *
     * @param resourceCompany 리소스가 속한 기관 (null이면 접근 거부)
     * @throws CustomException 요청자를 확인할 수 없거나(401/403) 다른 기관의 리소스인 경우(403)
     */
    @Transactional(readOnly = true)
    public void requireSameCompany(Company resourceCompany) {
        requireSameCompany(resourceCompany != null ? resourceCompany.getId() : null);
    }

    /** {@link #requireSameCompany(Company)}의 ID 버전 */
    @Transactional(readOnly = true)
    public void requireSameCompany(Long resourceCompanyId) {
        Long callerCompanyId = currentCompanyId();

        if (resourceCompanyId == null || !resourceCompanyId.equals(callerCompanyId)) {
            log.warn("[ResourceScope] 타 기관 리소스 접근 차단: 요청자 companyId={}, 리소스 companyId={}",
                    callerCompanyId, resourceCompanyId);
            throw new CustomException("해당 정보에 접근할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }
    }

    /** 현재 요청자의 소속 기관 ID. 스케줄러 등 인증 컨텍스트가 없는 실행에서는 예외. */
    private Long currentCompanyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new CustomException("인증 정보가 없습니다", HttpStatus.UNAUTHORIZED);
        }

        Optional<Long> companyId = callerCompanyResolver.resolveCompanyId(authentication.getName());
        return companyId.orElseThrow(
                () -> new CustomException("소속 기관을 확인할 수 없습니다", HttpStatus.FORBIDDEN));
    }
}
