package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

/**
 * 결재 서명 관리. AppUser(관리자)·Member(직원) 공용 — JWT principal로 소유자를 해석한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SignatureService {

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    /** 내 서명 조회. 전체 S3 URL 반환 (없으면 null). */
    @Transactional(readOnly = true)
    public String getMySignatureUrl(UserDetails userDetails) {
        String path = resolveOwner(userDetails).signatureUrl();
        return toAbsoluteUrl(path);
    }

    /**
     * 내 서명 등록 (base64 PNG).
     *
     * <p>기존 서명 파일은 지우지 않는다. 결재 단계·회의록 서명은 서명할 때의 파일 경로를 그대로 가리키므로,
     * 지우면 지난 문서의 서명이 깨진다. 2026-11-16 숲속에서 원장이 서명을 두 번 바꾸며 지난 결재 372건·회의록 5건의
     * 서명 그림이 S3에서 사라졌다(버킷 버전 보관이 꺼져 있어 되살리지 못함). 서명 파일은 수십 KB라 남겨 둔다.
     */
    public String registerMySignature(UserDetails userDetails, String imageBase64) throws IOException {
        Owner owner = resolveOwner(userDetails);

        byte[] bytes = decodePngBase64(imageBase64);
        String newPath = fileStorageService.storeBytes(bytes, ".png", "signatures");

        owner.updateSignature(newPath);

        log.info("[Signature] 서명 등록: owner={}, path={}", owner.describe(), newPath);
        return toAbsoluteUrl(newPath);
    }

    /** 내 서명 삭제 — 등록만 지운다. 파일은 지난 문서가 가리키고 있을 수 있어 남긴다(위 registerMySignature 참고) */
    public void deleteMySignature(UserDetails userDetails) {
        Owner owner = resolveOwner(userDetails);
        owner.updateSignature(null);
        log.info("[Signature] 서명 삭제: owner={}", owner.describe());
    }

    // ─── 내부 헬퍼 ───

    static byte[] decodePngBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("이미지 데이터가 비어 있습니다.");
        }

        String payload = base64.trim();
        int commaIndex = payload.indexOf(',');
        if (payload.startsWith("data:") && commaIndex > 0) {
            payload = payload.substring(commaIndex + 1);
        }

        byte[] bytes = Base64.getDecoder().decode(payload);
        if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47) {
            throw new IllegalArgumentException("이미지는 PNG 형식이어야 합니다.");
        }

        return bytes;
    }

    private String toAbsoluteUrl(String path) {
        if (path == null || path.isEmpty() || path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return fileStorageService.getFileUrl(path);
    }

    /** JWT principal → 서명 소유자 해석 (Member 우선, 그 다음 AppUser — 기존 관례) */
    private Owner resolveOwner(UserDetails userDetails) {
        if (userDetails == null) {
            throw new SecurityException("인증 정보가 없습니다");
        }

        String username = userDetails.getUsername();

        Optional<Member> member = memberRepository.findByUsername(username);
        if (member.isPresent()) {
            Member found = member.get();
            return new Owner(found.getSignatureUrl(), found::updateSignature, "member:" + found.getId());
        }

        Optional<AppUser> appUser = userRepository.findByEmail(username);
        if (appUser.isPresent()) {
            AppUser found = appUser.get();
            return new Owner(found.getSignatureUrl(), found::updateSignature, "admin:" + found.getId());
        }

        throw new SecurityException("사용자를 찾을 수 없습니다");
    }

    private record Owner(String signatureUrl, java.util.function.Consumer<String> updater, String describe) {
        void updateSignature(String path) {
            updater.accept(path);
        }
    }
}
