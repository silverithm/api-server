package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
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
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserSigninDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.security.auth.message.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
    private GeocodingService geocodingService;

    private Key secretKey;


    public UserService(@Value("${jwt.secretKey}") String secretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }


    @Transactional
    public SigninResponseDTO signin(UserSigninDTO userSigninDTO) {
        try {

            AppUser findUser = userRepository.findByEmail(userSigninDTO.getEmail())
                    .orElseThrow(() -> new CustomException("User Not Found", HttpStatus.UNPROCESSABLE_ENTITY));

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(userSigninDTO.getEmail(), userSigninDTO.getPassword()));

            TokenInfo tokenInfo = jwtTokenProvider.generateToken(userSigninDTO.getEmail(),
                    Collections.singleton(findUser.getUserRole()));

            findUser.update(tokenInfo.getRefreshToken());

            if (findUser.getSubscription() == null) {
                return new SigninResponseDTO(findUser.getId(), findUser.getUsername(), findUser.getCompany().getId(),
                        findUser.getCompany().getName(),
                        findUser.getCompany().getCompanyAddress(), findUser.getCompany().getAddressName(),
                        tokenInfo, new SubscriptionResponseDTO(), findUser.getCustomerKey());
            }

            return new SigninResponseDTO(findUser.getId(), findUser.getUsername(), findUser.getCompany().getId(),
                    findUser.getCompany().getName(),
                    findUser.getCompany().getCompanyAddress(), findUser.getCompany().getAddressName(),
                    tokenInfo, new SubscriptionResponseDTO(findUser.getSubscription()), findUser.getCustomerKey());

        } catch (AuthenticationException e) {
            throw new CustomException("Invalid username/password supplied", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Transactional
    public TokenInfo signup(UserDataDTO userDataDTO) throws Exception {
        validateEmailNotExists(userDataDTO.getEmail());

        TokenInfo tokenInfo = generateTokenInfo(userDataDTO);
        Location companyLocation = geocodingService.getAddressCoordinates(userDataDTO.getCompanyAddress());
        Company company = new Company(userDataDTO.getCompanyName(), userDataDTO.getCompanyAddress(), companyLocation);
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
        if (userRepository.existsByEmail(email)) {
            throw new CustomException("Useremail is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
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
        //Access Token 검증
        if (!jwtTokenProvider.validateToken(accessToken)) {
        }

        Claims claims = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(accessToken)
                .getBody();

        String userEmail = claims.getSubject();
        long time = claims.getExpiration().getTime() - System.currentTimeMillis();

        //Access Token blacklist에 등록하여 만료시키기
        //해당 엑세스 토큰의 남은 유효시간을 얻음
        redisUtils.setBlackList(accessToken, userEmail, time);
        //DB에 저장된 Refresh Token 제거
//        refreshTokenRepository.deleteById(userEmail);

        AppUser findUser = userRepository.findByUsername(userEmail);
        findUser.updateRefreshToken(null);
    }

    public AppUser loadUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public FindPasswordResponse findPassword(String email) {
        AppUser findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User Not Found", HttpStatus.UNPROCESSABLE_ENTITY));

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
        String subject = "실버리즘 임시 비밀번호 발급";
        String content = temporaryPassword;

        emailService.sendEmailAsync(email, subject, content);
    }

    public void changePassword(PasswordChangeRequest passwordChangeRequest) {
        AppUser findUser = userRepository.findByEmail(passwordChangeRequest.email())
                .orElseThrow(() -> new CustomException("User Not Found", HttpStatus.UNPROCESSABLE_ENTITY));

        if (!passwordEncoder.matches(passwordChangeRequest.currentPassword(), findUser.getPassword())) {
            throw new CustomException("Invalid current password", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String encodedPassword = passwordEncoder.encode(passwordChangeRequest.newPassword());
        findUser.updatePassword(encodedPassword);

        userRepository.save(findUser);
    }

    @Transactional
    public void updateCompanyName(UpdateCompanyNameDTO updateCompanyNameDTO, String userEmail) {
        AppUser findUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException("User Not Found", HttpStatus.UNPROCESSABLE_ENTITY));
        findUser.updateCompanyName(updateCompanyNameDTO.companyName());
    }

    @Transactional
    public UpdateCompanyAddressResponse updateCompanyAddress(UpdateCompanyAddressDTO updateCompanyAddressDTO,
                                                             String userEmail)
            throws Exception {
        AppUser findUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException("User Not Found", HttpStatus.UNPROCESSABLE_ENTITY));
        Location companyLocation = geocodingService.getAddressCoordinates(updateCompanyAddressDTO.companyAddress());
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

        log.info("refresh Toekn !!! : " + new Date());

        String userName = jwtTokenProvider.getUsernameFromToken(tokenRefreshRequest.refreshToken());
        Authentication authentication = jwtTokenProvider.getAuthentication(tokenRefreshRequest.refreshToken());

        if (!jwtTokenProvider.validateToken(tokenRefreshRequest.refreshToken())) {
            throw new CustomException("Invalid Refresh Token", HttpStatus.UNAUTHORIZED);
        }

        UserResponseDTO.TokenInfo tokenInfo = jwtTokenProvider.generateToken(userName,
                authentication.getAuthorities());

        return tokenInfo;
    }


    public SubscriptionResponseDTO getUserSubscription(String userEmail) {
        AppUser user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException("User Not Found", HttpStatus.NOT_FOUND));

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

            log.info("userEmail: {}", userEmail);

            // 사용자 정보 조회
            AppUser user = userRepository.findByEmail(userEmail)
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
}

