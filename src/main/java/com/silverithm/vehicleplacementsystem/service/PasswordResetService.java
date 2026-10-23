package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.FindPasswordResponse;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 찾기 — 이메일로 보낸 일회용 링크를 눌러 새 비밀번호를 정했을 때만 바뀐다.
 *
 * <p>전에는 "비밀번호 찾기"만 눌러도 곧바로 임시 비밀번호로 덮어써서, 이메일 주소만 아는 누구나
 * 남의 계정을 잠가 버릴 수 있었다(2026-10-23 숲속어르신학교 관리자 계정). 이제 요청만으로는
 * 아무것도 바뀌지 않는다. 가입 여부는 응답으로 드러내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final String KIND_ADMIN = "ADMIN";
    static final String KIND_MEMBER = "MEMBER";
    static final int TOKEN_TTL_MINUTES = 30;
    /** 같은 이메일로 재설정 메일이 연달아 가지 않게 하는 간격 */
    static final int COOLDOWN_MINUTES = 2;
    static final String SENT_MESSAGE =
            "가입된 이메일이라면 비밀번호 재설정 링크를 보냈습니다. 메일의 링크를 눌러 " + TOKEN_TTL_MINUTES + "분 안에 새 비밀번호를 정해 주세요.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisUtils redisUtils;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    @Value("${app.web-base-url:https://www.carev.kr}")
    private String webBaseUrl;

    /** 관리자(웹 가입자)용 */
    public FindPasswordResponse requestForAdmin(String email) {
        return request(KIND_ADMIN, email, userRepository.findActiveByEmail(email).isPresent());
    }

    /** 직원(앱 가입자)용 */
    public FindPasswordResponse requestForMember(String email) {
        return request(KIND_MEMBER, email, memberRepository.findActiveMember(email).isPresent());
    }

    private FindPasswordResponse request(String kind, String email, boolean exists) {
        if (email == null || email.isBlank()) {
            throw new CustomException("이메일을 입력해 주세요", HttpStatus.BAD_REQUEST);
        }
        String normalized = email.trim();
        if (!exists) {
            log.info("[PasswordReset] 가입 없는 이메일 요청: kind={} email={}", kind, PrivacyMask.email(normalized));
            return new FindPasswordResponse(SENT_MESSAGE);
        }
        if (!redisUtils.tryAcquireOnce("pwreset:cooldown:" + kind + ":" + normalized.toLowerCase(), COOLDOWN_MINUTES)) {
            log.info("[PasswordReset] 재요청 간격 안: kind={} email={}", kind, PrivacyMask.email(normalized));
            return new FindPasswordResponse(SENT_MESSAGE);
        }
        String token = newToken();
        redisUtils.set(tokenKey(token), kind + ":" + normalized, TOKEN_TTL_MINUTES);
        String link = webBaseUrl + "/reset-password?token=" + token;
        try {
            emailService.sendPasswordResetEmail(normalized, link, TOKEN_TTL_MINUTES);
        } catch (Exception e) {
            // 보냈는지 여부로 가입 여부가 드러나지 않게 같은 응답을 준다 — 실패는 로그로만 남긴다
            log.error("[PasswordReset] 메일 전송 실패: kind={} email={}", kind, PrivacyMask.email(normalized), e);
        }
        log.info("[PasswordReset] 재설정 링크 발송: kind={} email={}", kind, PrivacyMask.email(normalized));
        return new FindPasswordResponse(SENT_MESSAGE);
    }

    /** 링크가 아직 유효한지(화면에서 미리 알려 주기 위해). 토큰은 소비하지 않는다 */
    public boolean isTokenValid(String token) {
        return token != null && !token.isBlank() && redisUtils.hasKey(tokenKey(token));
    }

    @Transactional
    public void confirm(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 100) {
            throw new CustomException("비밀번호는 8자 이상 100자 이하여야 합니다", HttpStatus.BAD_REQUEST);
        }
        Optional<String> owner = token == null || token.isBlank()
                ? Optional.empty()
                : Optional.ofNullable(redisUtils.get(tokenKey(token))).map(Object::toString);
        String stored = owner.orElseThrow(() ->
                new CustomException("링크가 만료되었거나 이미 사용되었습니다. 비밀번호 찾기를 다시 해 주세요.", HttpStatus.BAD_REQUEST));
        // 한 번 쓰면 사라진다
        redisUtils.delete(tokenKey(token));

        int split = stored.indexOf(':');
        String kind = stored.substring(0, split);
        String email = stored.substring(split + 1);
        String encoded = passwordEncoder.encode(newPassword);

        if (KIND_ADMIN.equals(kind)) {
            AppUser user = userRepository.findActiveByEmail(email)
                    .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.BAD_REQUEST));
            user.updatePassword(encoded);
            user.updateRefreshToken(null);
            userRepository.save(user);
        } else {
            Member member = memberRepository.findActiveMember(email)
                    .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.BAD_REQUEST));
            member.updatePassword(encoded);
            memberRepository.save(member);
        }
        log.info("[PasswordReset] 비밀번호 재설정 완료: kind={} email={}", kind, PrivacyMask.email(email));
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 레디스에는 토큰 자체가 아니라 해시만 둔다 — 키가 새도 링크를 만들 수 없다 */
    static String tokenKey(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return "pwreset:token:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
