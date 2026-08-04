package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.FileOwnershipRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업로드 파일 접근 통제.
 *
 * <p>파일 저장 경로만 알면 인증된 아무 사용자나 타 기관의 결재 첨부파일·서명 이미지·채팅 파일을
 * 내려받을 수 있었기 때문에(IDOR), 다음 두 가지를 강제한다.
 * <ol>
 *   <li>경로 형식 검증 — 상위 디렉터리 탈출(../), 절대경로, 인코딩 우회 차단</li>
 *   <li>귀속 검증 — 해당 경로가 요청자 소속 기관의 레코드에서 참조되고 있어야 함</li>
 * </ol>
 *
 * <p>업로드 직후 아직 어떤 레코드에도 연결되지 않은 파일은 귀속 검증을 통과할 수 없으므로,
 * 업로더 본인에 한해 짧은 유예 시간 동안 접근을 허용한다(초안 작성 중 미리보기 등).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileAccessGuard {

    /** 업로드 직후 업로더 본인에게 허용하는 유예 시간(분) */
    private static final int UPLOAD_GRACE_MINUTES = 60;

    private static final String GRACE_KEY_PREFIX = "file:upload:";

    /**
     * 허용 경로 형식: {카테고리}[/{하위}]/{파일명}.{확장자}
     * 예) approvals/9f1c….pdf, chat/12/9f1c….png, signatures/9f1c….png
     */
    private static final Pattern SAFE_PATH = Pattern.compile(
            "^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*/[A-Za-z0-9_.-]+\\.[A-Za-z0-9]{1,8}$");

    private final FileOwnershipRepository fileOwnershipRepository;
    private final CallerCompanyResolver callerCompanyResolver;
    private final FileStorageService fileStorageService;
    private final RedisUtils redisUtils;

    /**
     * 다운로드/삭제 요청 경로를 검증하고 정규화된 경로를 돌려준다.
     *
     * @throws CustomException 형식이 잘못됐거나(400) 접근 권한이 없는 경우(403)
     */
    @Transactional(readOnly = true)
    public String requireAccessible(UserDetails userDetails, String rawPath) {
        String path = normalize(rawPath);

        if (isWithinUploadGrace(userDetails, path)) {
            return path;
        }

        Long companyId = resolveCompanyId(userDetails);
        if (!isOwnedByCompany(companyId, path)) {
            log.warn("[FileAccess] 권한 없는 파일 접근 차단: companyId={}, path={}", companyId, path);
            throw new CustomException("해당 파일에 접근할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

        return path;
    }

    /** 업로드 직후 업로더 본인에게 유예 접근을 부여한다. */
    public void grantUploadGrace(UserDetails userDetails, String path) {
        if (userDetails == null || path == null || path.isBlank()) {
            return;
        }
        try {
            redisUtils.set(GRACE_KEY_PREFIX + path, userDetails.getUsername(), UPLOAD_GRACE_MINUTES);
        } catch (Exception e) {
            // 유예는 편의 기능일 뿐이므로 실패해도 업로드 자체를 막지 않는다.
            log.warn("[FileAccess] 업로드 유예 등록 실패(무시): {}", e.getMessage());
        }
    }

    // ─── 내부 ───

    /** 경로 형식 검증. 통과하면 그대로, 아니면 400. */
    private String normalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new CustomException("파일 경로가 비어 있습니다", HttpStatus.BAD_REQUEST);
        }

        String path = rawPath.trim();

        // 앱은 API 응답에 실린 절대 S3 URL(attachmentUrl)을 그대로 path로 보낸다.
        // 우리 버킷 URL에 한해 상대 경로로 정규화해 받아준다 (다른 호스트는 아래 검사에서 거부).
        String selfPrefix = selfBucketPrefix();
        if (selfPrefix != null && path.startsWith(selfPrefix)) {
            path = path.substring(selfPrefix.length());
        }

        boolean unsafe = path.startsWith("/")
                || path.contains("..")
                || path.contains("\\")
                || path.contains("%")
                || path.contains("//")
                || path.contains(":")
                || !SAFE_PATH.matcher(path).matches();

        if (unsafe) {
            log.warn("[FileAccess] 비정상 파일 경로 차단: {}", path);
            throw new CustomException("올바르지 않은 파일 경로입니다", HttpStatus.BAD_REQUEST);
        }

        return path;
    }

    /** 우리 S3 버킷의 공개 URL 접두사 (예: https://bucket.s3.region.amazonaws.com/carev/) */
    private String selfBucketPrefix() {
        try {
            String prefix = fileStorageService.getFileUrl("");
            return (prefix == null || prefix.isBlank()) ? null : prefix;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isWithinUploadGrace(UserDetails userDetails, String path) {
        if (userDetails == null) {
            return false;
        }
        try {
            Object uploader = redisUtils.get(GRACE_KEY_PREFIX + path);
            return uploader != null && uploader.toString().equals(userDetails.getUsername());
        } catch (Exception e) {
            log.warn("[FileAccess] 업로드 유예 조회 실패(무시): {}", e.getMessage());
            return false;
        }
    }

    /** JWT principal → 소속 기관 */
    private Long resolveCompanyId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException("인증 정보가 없습니다", HttpStatus.UNAUTHORIZED);
        }

        return callerCompanyResolver.resolveCompanyId(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("소속 기관을 확인할 수 없습니다", HttpStatus.FORBIDDEN));
    }

    private boolean isOwnedByCompany(Long companyId, String path) {
        Set<String> candidates = referenceCandidates(path);

        return fileOwnershipRepository.existsApprovalAttachment(companyId, candidates)
                || fileOwnershipRepository.existsTemplateFile(companyId, candidates)
                || fileOwnershipRepository.existsChatFile(companyId, candidates)
                || fileOwnershipRepository.existsApprovalStepSignature(companyId, candidates)
                || fileOwnershipRepository.existsAdminSignature(companyId, candidates)
                || fileOwnershipRepository.existsMemberSignature(companyId, candidates)
                || fileOwnershipRepository.existsCompanySeal(companyId, candidates);
    }

    /**
     * 같은 파일이 컬럼에 따라 상대 경로 또는 절대 S3 URL로 저장돼 있으므로 두 표기를 모두 후보로 만든다.
     * (예: 채팅은 절대 URL, 결재 양식은 상대 경로)
     */
    private Set<String> referenceCandidates(String path) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(path);

        try {
            String absoluteUrl = fileStorageService.getFileUrl(path);
            if (absoluteUrl != null && !absoluteUrl.isBlank()) {
                candidates.add(absoluteUrl);
            }
        } catch (Exception e) {
            log.warn("[FileAccess] S3 URL 생성 실패(상대 경로만 대조): {}", e.getMessage());
        }

        return candidates;
    }
}
