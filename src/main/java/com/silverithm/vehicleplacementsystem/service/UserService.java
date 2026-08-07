package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.FCMTokenUpdateDTO;
import com.silverithm.vehicleplacementsystem.dto.FindPasswordResponse;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.PasswordChangeRequest;
import com.silverithm.vehicleplacementsystem.dto.SigninResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.TokenRefreshRequest;
import com.silverithm.vehicleplacementsystem.dto.TokenResponse;
import com.silverithm.vehicleplacementsystem.dto.TokenValidationResponse;
import com.silverithm.vehicleplacementsystem.dto.UpdateCompanyAddressDTO;
import com.silverithm.vehicleplacementsystem.dto.UpdateCompanyAddressResponse;
import com.silverithm.vehicleplacementsystem.dto.UpdateCompanyNameDTO;
import com.silverithm.vehicleplacementsystem.dto.UserInfoResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserSigninDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.entity.UserRole;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.security.auth.message.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.orm.hibernate5.SpringSessionContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;


@Slf4j
@Service
public class UserService {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private SlackService slackService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyCodeService companyCodeService;
    @Autowired
    private FileStorageService fileStorageService;

    private Key secretKey;


    public UserService(@Value("${jwt.secretKey}") String secretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }


    @Transactional(readOnly = true)
    public SigninResponseDTO signin(UserSigninDTO userSigninDTO) {
        try {

            AppUser findUser = userRepository.findActiveByEmail(userSigninDTO.getEmail())
                    .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(userSigninDTO.getEmail(), userSigninDTO.getPassword()));

            TokenInfo tokenInfo = jwtTokenProvider.generateToken(userSigninDTO.getEmail(),
                    Collections.singleton(findUser.getUserRole()));

            findUser.update(tokenInfo.getRefreshToken());

            if (findUser.getSubscription() == null) {
                return new SigninResponseDTO(findUser.getId(), findUser.getUsername(), findUser.getCompany().getId(),
                        findUser.getCompany().getName(),
                        findUser.getCompany().getCompanyAddress(), findUser.getCompany().getAddressName(),
                        findUser.getCompany().getCompanyCode(),
                        tokenInfo, new SubscriptionResponseDTO(), findUser.getCustomerKey());
            }

            return new SigninResponseDTO(findUser.getId(), findUser.getUsername(), findUser.getCompany().getId(),
                    findUser.getCompany().getName(),
                    findUser.getCompany().getCompanyAddress(), findUser.getCompany().getAddressName(),
                    findUser.getCompany().getCompanyCode(),
                    tokenInfo, new SubscriptionResponseDTO(findUser.getSubscription()), findUser.getCustomerKey());

        } catch (AuthenticationException e) {
            throw new CustomException("아이디 또는 비밀번호가 올바르지 않습니다", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Transactional
    public TokenInfo signup(UserDataDTO userDataDTO) throws Exception {
        validateEmailNotExists(userDataDTO.getEmail());

        TokenInfo tokenInfo = generateTokenInfo(userDataDTO);
        Location companyLocation = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
        Company company = new Company(userDataDTO.getCompanyName(), userDataDTO.getCompanyAddress(), companyLocation);
        company.updateCompanyCode(companyCodeService.generateUniqueCode());
        String customerKey = generateUniqueCustomerKey();

        companyRepository.save(company);
        userRepository.save(
                AppUser.of(userDataDTO, passwordEncoder.encode(userDataDTO.getPassword()), tokenInfo, company,
                        customerKey));
        slackService.sendSignupSuccessNotification(userDataDTO.getEmail(), userDataDTO.getName(),
                userDataDTO.getCompanyName());

        return tokenInfo;
    }

    private void validateEmailNotExists(String email) throws Exception {
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new CustomException("이미 사용 중인 이메일입니다", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private TokenInfo generateTokenInfo(UserDataDTO userDataDTO) {
        return jwtTokenProvider.generateToken(
                userDataDTO.getName(),
                Collections.singleton(userDataDTO.getRole())
        );
    }


    private String generateUniqueCustomerKey() {
        String customerKey;
        do {
            customerKey = UUID.randomUUID().toString();
        } while (userRepository.existsByCustomerKey(customerKey));
        return customerKey;
    }

    @Transactional
    public void logout(HttpServletRequest request) {

        String accessToken = jwtTokenProvider.resolveToken(request);

        if (accessToken == null) {
            return;
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .setSigningKey(secretKey)
                    .parseClaimsJws(accessToken)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // 만료된 토큰이라도 claims를 추출하여 블랙리스트 처리
            claims = e.getClaims();
        } catch (Exception e) {
            log.warn("로그아웃 토큰 파싱 실패: {}", e.getMessage());
            return;
        }

        String userEmail = claims.getSubject();
        long time = claims.getExpiration().getTime() - System.currentTimeMillis();

        if (time > 0) {
            // Access Token blacklist에 등록하여 만료시키기
            redisUtils.setBlackList(accessToken, userEmail, time);
        }

        // DB에 저장된 Refresh Token 제거 (관리자만 해당, 직원은 DB에 저장 안 함)
        AppUser findUser = userRepository.findByUsername(userEmail);
        if (findUser != null) {
            findUser.updateRefreshToken(null);
        }
    }

    public AppUser loadUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public FindPasswordResponse findPassword(String email) {
        AppUser findUser = userRepository.findActiveByEmail(email)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));

        String temporaryPassword = createTemporaryPassword(findUser);

        try {
            sendTemporaryPasswordEmail(email, temporaryPassword);
            return new FindPasswordResponse("임시 비밀번호가 이메일로 전송되었습니다.");
        } catch (Exception e) {
            throw new CustomException("이메일 전송에 실패했습니다. : " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String createTemporaryPassword(AppUser user) {
        String temporaryPassword = generateRandomPassword(10);

        String encodedPassword = passwordEncoder.encode(temporaryPassword);

        user.updatePassword(encodedPassword);
        userRepository.save(user);

        return temporaryPassword;
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(chars.length());
            sb.append(chars.charAt(randomIndex));
        }

        return sb.toString();
    }

    // 이메일 전송
    private void sendTemporaryPasswordEmail(String email, String temporaryPassword) {
        String subject = "케어브이 임시 비밀번호 발급";
        String content = temporaryPassword;

        emailService.sendEmailAsync(email, subject, content);
    }

    public void changePassword(String authenticatedEmail, PasswordChangeRequest passwordChangeRequest) {
        AppUser findUser = userRepository.findActiveByEmail(authenticatedEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));

        if (!passwordEncoder.matches(passwordChangeRequest.currentPassword(), findUser.getPassword())) {
            throw new CustomException("현재 비밀번호가 올바르지 않습니다", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String encodedPassword = passwordEncoder.encode(passwordChangeRequest.newPassword());
        findUser.updatePassword(encodedPassword);

        userRepository.save(findUser);
    }

    @Transactional
    public void updateCompanyName(UpdateCompanyNameDTO updateCompanyNameDTO, String userEmail) {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));
        findUser.updateCompanyName(updateCompanyNameDTO.companyName());
    }

    @Transactional
    public UpdateCompanyAddressResponse updateCompanyAddress(UpdateCompanyAddressDTO updateCompanyAddressDTO,
                                                             String userEmail)
            throws Exception {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));
        Location companyLocation = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
        findUser.updateCompanyAddress(companyLocation, updateCompanyAddressDTO.companyAddress());

        return new UpdateCompanyAddressResponse(updateCompanyAddressDTO.companyAddress(), companyLocation);
    }

    public Integer getDailyDispatchLimit(String username) {
        return redisUtils.getDailyDispatchLimit(username);
    }

    @Transactional
    public List<AppUser> updateCustomerKey() {
        List<AppUser> users = userRepository.findAll();
        users.forEach(user -> {
            String customerKey = generateUniqueCustomerKey();
            log.info(customerKey);
            user.updateCustomerKey(customerKey);
        });
        return users;
    }

    @Transactional
    public UserResponseDTO.TokenInfo refreshToken(TokenRefreshRequest tokenRefreshRequest) {

        log.info("refresh Token !!! : " + new Date());

        // validateToken은 만료 토큰에 ExpiredJwtException을 던진다.
        // 잡지 않으면 generic 핸들러로 떨어져 500이 나가고, 클라이언트가
        // "재로그인 필요"와 "서버 장애"를 구분할 수 없다. (운영 500 로그의 주 원인)
        boolean valid;
        try {
            valid = jwtTokenProvider.validateToken(tokenRefreshRequest.refreshToken());
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new CustomException("리프레시 토큰이 만료되었습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);
        }
        if (!valid) {
            throw new CustomException("유효하지 않은 리프레시 토큰입니다", HttpStatus.UNAUTHORIZED);
        }

        // access 토큰이 refresh 용도로 사용되는 것을 방지
        if (!jwtTokenProvider.isRefreshToken(tokenRefreshRequest.refreshToken())) {
            throw new CustomException("리프레시 토큰이 아닙니다", HttpStatus.UNAUTHORIZED);
        }

        String userName = jwtTokenProvider.getUsernameFromToken(tokenRefreshRequest.refreshToken());
        Authentication authentication = jwtTokenProvider.getAuthentication(tokenRefreshRequest.refreshToken());

        UserResponseDTO.TokenInfo tokenInfo = jwtTokenProvider.generateToken(userName,
                authentication.getAuthorities());

        return tokenInfo;
    }


    public SubscriptionResponseDTO getUserSubscription(String userEmail) {
        AppUser user = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (user.getSubscription() == null) {
            return new SubscriptionResponseDTO();
        }

        return new SubscriptionResponseDTO(user.getSubscription());
    }

    /**
     * JWT 토큰 유효성 검증
     *
     * @param token 검증할 JWT 토큰
     * @return TokenValidationResponse 토큰 검증 결과
     */
    public TokenValidationResponse validateToken(String token) {
        try {
            // 토큰이 null이거나 빈 문자열인지 확인
            if (token == null || token.trim().isEmpty()) {
                return TokenValidationResponse.fail("토큰이 제공되지 않았습니다.");
            }

            // JWT 토큰 기본 형식 검증
            if (!jwtTokenProvider.validateToken(token)) {
                return TokenValidationResponse.fail("유효하지 않은 토큰입니다.");
            }

            // 토큰이 블랙리스트에 있는지 확인 (로그아웃된 토큰인지)
            if (redisUtils.hasKeyBlackList(token)) {
                return TokenValidationResponse.fail("로그아웃된 토큰입니다.");
            }

            // 토큰 파싱하여 클레임 정보 추출
            Claims claims = Jwts.parser()
                    .setSigningKey(secretKey)
                    .parseClaimsJws(token)
                    .getBody();

            String userEmail = claims.getSubject();
            Date expiration = claims.getExpiration();

            // 토큰 만료 시간 확인
            if (expiration.before(new Date())) {
                return TokenValidationResponse.fail("만료된 토큰입니다.");
            }

            log.info("userEmail: {}", PrivacyMask.email(userEmail));

            // 사용자 정보 조회
            AppUser user = userRepository.findActiveByEmail(userEmail)
                    .orElse(null);

            if (user == null) {
                return TokenValidationResponse.fail("존재하지 않는 사용자입니다.");
            }

            // 토큰 검증 성공
            return TokenValidationResponse.success(
                    user.getEmail(),
                    user.getUsername(),
                    user.getId(),
                    expiration.getTime()
            );

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return TokenValidationResponse.fail("만료된 토큰입니다.");
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            return TokenValidationResponse.fail("지원되지 않는 토큰 형식입니다.");
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            return TokenValidationResponse.fail("잘못된 형식의 토큰입니다.");
        } catch (io.jsonwebtoken.security.SignatureException e) {
            return TokenValidationResponse.fail("토큰 서명이 유효하지 않습니다.");
        } catch (IllegalArgumentException e) {
            return TokenValidationResponse.fail("토큰이 올바르지 않습니다.");
        } catch (Exception e) {
            log.error("토큰 검증 중 오류 발생: ", e);
            return TokenValidationResponse.fail("토큰 검증 중 오류가 발생했습니다.");
        }
    }

    /**
     * HTTP 요청에서 토큰을 추출하여 검증
     *
     * @param request HTTP 요청
     * @return TokenValidationResponse 토큰 검증 결과
     */
    public TokenValidationResponse validateTokenFromRequest(HttpServletRequest request) {
        try {
            String token = jwtTokenProvider.resolveToken(request);

            if (token == null) {
                return TokenValidationResponse.fail("요청에서 토큰을 찾을 수 없습니다.");
            }

            return validateToken(token);

        } catch (Exception e) {
            log.error("요청에서 토큰 검증 중 오류 발생: ", e);
            return TokenValidationResponse.fail("토큰 검증 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public void deleteUser(String userEmail) {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        findUser.getSubscription().updateStatus(SubscriptionStatus.INACTIVE);
        findUser.getCompany().updateExpose(false);
        findUser.softDelete();

    }

    public UserInfoResponseDTO getUserInfo(String userEmail) {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        String companySealUrl = resolveCompanySealUrl(findUser.getCompany());
        String companyHomepageUrl = findUser.getCompany() != null ? findUser.getCompany().getHomepageUrl() : null;

        if (findUser.getSubscription() == null) {
            return new UserInfoResponseDTO(findUser.getId(), findUser.getUsername(), findUser.getEmail(),
                    findUser.getCompany().getId(), findUser.getCompany().getName(),
                    findUser.getCompany().getCompanyAddress(), findUser.getCompany().getAddressName(),
                    findUser.getCompany().getCompanyCode(),
                    new SubscriptionResponseDTO(), findUser.getCustomerKey(), companySealUrl, companyHomepageUrl);
        }

        return new UserInfoResponseDTO(findUser.getId(), findUser.getUsername(), findUser.getEmail(),
                findUser.getCompany().getId(), findUser.getCompany().getName(),
                findUser.getCompany().getCompanyAddress(), findUser.getCompany().getAddressName(),
                findUser.getCompany().getCompanyCode(),
                new SubscriptionResponseDTO(findUser.getSubscription()), findUser.getCustomerKey(), companySealUrl,
                companyHomepageUrl);
    }

    /** 푸시 알림 수신 여부 조회 (값이 없던 기존 계정은 받는 것으로 본다) */
    @Transactional(readOnly = true)
    public boolean isPushEnabled(String userEmail) {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));
        return !Boolean.FALSE.equals(findUser.getPushEnabled());
    }

    /** 푸시 알림 수신 on/off */
    @Transactional
    public void updatePushEnabled(String userEmail, boolean enabled) {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));
        findUser.updatePushEnabled(enabled);
        log.info("[User Service] 알림 수신 설정 변경: userId={}, enabled={}", findUser.getId(), enabled);
    }

    /**
     * 기관 홈페이지 주소 등록/해제 (ROLE_ADMIN 전용).
     *
     * 사이드바에서 새 탭으로 열리는 링크라 스킴을 http/https로 못박는다.
     * javascript: 같은 스킴이 들어오면 클릭 한 번으로 스크립트가 실행되기 때문이다.
     * 스킴 없이 넣는 경우가 흔해서 그때는 https://를 붙여준다.
     */
    @Transactional
    public String updateCompanyHomepageUrl(String homepageUrl, String userEmail) {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));

        if (findUser.getUserRole() != UserRole.ROLE_ADMIN) {
            throw new SecurityException("기관 홈페이지는 관리자만 등록할 수 있습니다");
        }

        String normalized = normalizeHomepageUrl(homepageUrl);
        findUser.getCompany().updateHomepageUrl(normalized);

        log.info("[User Service] 기관 홈페이지 {}: companyId={}",
                normalized == null ? "해제" : "등록", findUser.getCompany().getId());
        return normalized;
    }

    /**
     * 기관이 함께 운영하는 주소들을 통째로 교체한다 (블로그·밴드 등).
     *
     * 첫 항목이 대표가 되어 homepage_url에도 반영된다 — 공문 발신부가 그 값을 쓰기 때문이다.
     * 주소 검증은 단일 등록과 같은 규칙(http/https만, 최대 500자)을 그대로 쓴다.
     */
    @Transactional
    public List<Map<String, String>> updateCompanyHomepageLinks(List<Map<String, String>> links, String userEmail) {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));

        if (findUser.getUserRole() != UserRole.ROLE_ADMIN) {
            throw new SecurityException("기관 홈페이지는 관리자만 등록할 수 있습니다");
        }
        if (links != null && links.size() > 10) {
            throw new IllegalArgumentException("홈페이지는 최대 10개까지 등록할 수 있습니다");
        }

        List<Map<String, String>> normalized = new java.util.ArrayList<>();
        if (links != null) {
            for (Map<String, String> link : links) {
                String url = normalizeHomepageUrl(link.get("url"));
                if (url == null) continue;   // 주소가 비면 그 줄은 등록하지 않는다
                String rawName = link.get("name") == null ? "" : link.get("name").trim();
                if (rawName.length() > 30) {
                    throw new IllegalArgumentException("홈페이지 이름은 30자까지 입력할 수 있습니다");
                }
                normalized.add(Map.of("name", rawName.isBlank() ? "홈페이지" : rawName, "url", url));
            }
        }

        Company company = findUser.getCompany();
        try {
            company.updateHomepageLinks(
                    normalized.isEmpty() ? null : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(normalized));
        } catch (Exception e) {
            throw new IllegalArgumentException("홈페이지 목록을 저장하지 못했습니다");
        }
        // 대표 주소(공문 발신부에 찍히는 값)는 항상 첫 항목과 맞춘다
        company.updateHomepageUrl(normalized.isEmpty() ? null : normalized.get(0).get("url"));

        log.info("[User Service] 기관 홈페이지 목록 저장: companyId={}, count={}", company.getId(), normalized.size());
        return normalized;
    }

    private String normalizeHomepageUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String url = raw.trim();
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            // 스킴이 아예 없을 때만 붙인다. 다른 스킴(javascript: 등)은 아래에서 걸러진다.
            if (lower.contains("://")) {
                throw new IllegalArgumentException("홈페이지 주소는 http 또는 https로 시작해야 합니다");
            }
            url = "https://" + url;
        }
        if (url.length() > 500) {
            throw new IllegalArgumentException("홈페이지 주소가 너무 깁니다 (최대 500자)");
        }
        try {
            java.net.URI parsed = java.net.URI.create(url);
            if (parsed.getHost() == null || parsed.getHost().isBlank()) {
                throw new IllegalArgumentException("홈페이지 주소를 확인해주세요");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("홈페이지 주소를 확인해주세요");
        }
        return url;
    }

    private String resolveCompanySealUrl(Company company) {
        if (company == null || company.getSealUrl() == null || company.getSealUrl().isEmpty()) {
            return null;
        }
        String sealUrl = company.getSealUrl();
        if (sealUrl.startsWith("http://") || sealUrl.startsWith("https://")) {
            return sealUrl;
        }
        try {
            return fileStorageService.getFileUrl(sealUrl);
        } catch (Exception e) {
            log.warn("[User Service] 직인 URL 변환 실패: {}", e.getMessage());
            return null;
        }
    }

    /** 기관 직인 등록 (ROLE_ADMIN 전용) */
    @Transactional
    public String updateCompanySeal(String imageBase64, String userEmail) {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));

        if (findUser.getUserRole() != UserRole.ROLE_ADMIN) {
            throw new SecurityException("기관 직인은 관리자만 등록할 수 있습니다");
        }

        try {
            byte[] bytes = SignatureService.decodePngBase64(imageBase64);
            String newPath = fileStorageService.storeBytes(bytes, ".png", "seals");

            deleteSealFileQuietly(findUser.getCompany().getSealUrl());
            findUser.getCompany().updateSeal(newPath);

            log.info("[User Service] 기관 직인 등록: companyId={}, path={}", findUser.getCompany().getId(), newPath);
            return fileStorageService.getFileUrl(newPath);
        } catch (SecurityException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException("직인 등록에 실패했습니다: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** 기관 직인 삭제 (ROLE_ADMIN 전용) */
    @Transactional
    public void deleteCompanySeal(String userEmail) {
        AppUser findUser = userRepository.findActiveByEmail(userEmail)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY));

        if (findUser.getUserRole() != UserRole.ROLE_ADMIN) {
            throw new SecurityException("기관 직인은 관리자만 삭제할 수 있습니다");
        }

        deleteSealFileQuietly(findUser.getCompany().getSealUrl());
        findUser.getCompany().updateSeal(null);
        log.info("[User Service] 기관 직인 삭제: companyId={}", findUser.getCompany().getId());
    }

    private void deleteSealFileQuietly(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        try {
            fileStorageService.deleteFile(path);
        } catch (Exception e) {
            log.warn("[User Service] 기존 직인 파일 삭제 실패(무시): {}", e.getMessage());
        }
    }

    @Transactional
    public void updateFcmToken(Long userId, FCMTokenUpdateDTO tokenUpdateDTO) {
        log.info("[User Service] FCM 토큰 업데이트: userId={}", userId);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + userId));

        user.updateFcmToken(tokenUpdateDTO.getFcmToken());
        userRepository.save(user);
        log.info("[User Service] FCM 토큰 업데이트 완료: userId={}", userId);
    }

    /** 로그아웃 시 기기 토큰 폐기 — 로그아웃한 기기로 알림이 가지 않도록 한다 */
    @Transactional
    public void clearFcmToken(Long userId) {
        log.info("[User Service] FCM 토큰 삭제: userId={}", userId);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + userId));

        user.updateFcmToken(null);
        userRepository.save(user);
    }
}
