package com.silverithm.vehicleplacementsystem.jwt;


import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    public static final long ACCESS_TOKEN_EXPIRE_TIME = 30 * 60 * 1000L;

    /**
     * 로그인 유지 기간.
     *
     * 7일이었는데, 앱을 매일 열지 않는 직원은 알림을 누를 때마다 로그인 화면을 만났다.
     * 알림에서 바로 그 대화·일정으로 들어가려면 로그인이 유지돼 있어야 하므로 90일로 둔다.
     * (액세스 토큰은 30분 그대로 — 실제 인증은 짧게 돌고, 이 값은 재로그인 주기다)
     */
    public static final long REFRESH_TOKEN_EXPIRE_TIME = 90L * 24 * 60 * 60 * 1000L;
    // 30 seconds
//    public static final long ACCESS_TOKEN_EXPIRE_TIME = 30 * 1000L;
//    // 2 minutes
//    public static final long REFRESH_TOKEN_EXPIRE_TIME = 2 * 60 * 1000L;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String AUTHORITIES_KEY = "auth";
    private static final String BEARER_TYPE = "Bearer";
    /** 로그인 주체 유형(ADMIN/MEMBER)과 그 id — CarevPrincipal 참고 */
    private static final String PRINCIPAL_TYPE_KEY = "ptype";
    private static final String PRINCIPAL_ID_KEY = "pid";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final Key key;

    //The specified key byte array is 248 bits which is not secure enough for any JWT HMAC-SHA algorithm.
    // The JWT JWA Specification (RFC 7518, Section 3.2) states that keys used with HMAC-SHA algorithms MUST have a size >= 256 bits (the key size must be greater than or equal to the hash output size).
    // Consider using the io.jsonwebtoken.security.Keys#secretKeyFor(SignatureAlgorithm) method to create a key guaranteed to be secure enough for your preferred HMAC-SHA algorithm.
    public JwtTokenProvider(@Value("${jwt.secretKey}") String secretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    //Authentication 을 가지고 AccessToken, RefreshToken 을 생성하는 메서드
    public UserResponseDTO.TokenInfo generateToken(Authentication authentication) {
        return generateToken(authentication.getName(), authentication.getAuthorities());
    }

    //name, authorities 를 가지고 AccessToken, RefreshToken 을 생성하는 메서드
    public UserResponseDTO.TokenInfo generateToken(String name,
                                                   Collection<? extends GrantedAuthority> inputAuthorities) {
        return generateToken(name, inputAuthorities, null, null);
    }

    /**
     * 로그인 주체(관리자 계정 / 직원)와 그 id까지 토큰에 담아 발급한다.
     *
     * 이 값이 있으면 서버는 "이 요청을 보낸 사람이 누구인지"를 클라이언트가 보낸 값에 기대지 않고
     * 토큰만으로 정할 수 있다. principalType/principalId가 null이면 예전처럼 이름만 담는다.
     */
    public UserResponseDTO.TokenInfo generateToken(String name,
                                                   Collection<? extends GrantedAuthority> inputAuthorities,
                                                   String principalType,
                                                   Long principalId) {
        //권한 가져오기
        String authorities = inputAuthorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Date now = new Date();

        //Generate AccessToken
        io.jsonwebtoken.JwtBuilder accessBuilder = Jwts.builder()
                .setSubject(name)
                .claim(AUTHORITIES_KEY, authorities)
                .claim("type", TYPE_ACCESS);
        addPrincipalClaims(accessBuilder, principalType, principalId);
        String accessToken = accessBuilder
                .setIssuedAt(now)   //토큰 발행 시간 정보
                .setExpiration(new Date(now.getTime() + ACCESS_TOKEN_EXPIRE_TIME))  //토큰 만료 시간 설정
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        //Generate RefreshToken
        io.jsonwebtoken.JwtBuilder refreshBuilder = Jwts.builder()
                .setSubject(name)
                .claim(AUTHORITIES_KEY, authorities)
                .claim("type", TYPE_REFRESH);
        addPrincipalClaims(refreshBuilder, principalType, principalId);
        String refreshToken = refreshBuilder
                .setIssuedAt(now)   //토큰 발행 시간 정보
                .setExpiration(new Date(now.getTime() + REFRESH_TOKEN_EXPIRE_TIME)) //토큰 만료 시간 설정
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return UserResponseDTO.TokenInfo.builder()
                .grantType(BEARER_TYPE)
                .accessToken(accessToken)
                .accessTokenExpirationTime(ACCESS_TOKEN_EXPIRE_TIME)
                .refreshToken(refreshToken)
                .refreshTokenExpirationTime(REFRESH_TOKEN_EXPIRE_TIME)
                .build();
    }

    //JWT 토큰을 복호화하여 토큰에 들어있는 정보를 꺼내는 메서드
    public Authentication getAuthentication(String accessToken) {
        //토큰 복호화
        Claims claims = parseClaims(accessToken);

        if (claims.get(AUTHORITIES_KEY) == null) {
            //TODO:: Change Custom Exception
            throw new CustomException("권한 정보가 없는 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        //클레임에서 권한 정보 가져오기
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        //UserDetails 객체를 만들어서 Authentication 리턴
        //(옛 토큰에는 신원 클레임이 없어 null이 들어간다 — 받는 쪽에서 DB로 메운다)
        UserDetails principal = new CarevPrincipal(
                claims.getSubject(), authorities, principalTypeOf(claims), principalIdOf(claims));
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    private void addPrincipalClaims(io.jsonwebtoken.JwtBuilder builder, String principalType, Long principalId) {
        if (principalType != null && principalId != null) {
            builder.claim(PRINCIPAL_TYPE_KEY, principalType).claim(PRINCIPAL_ID_KEY, principalId);
        }
    }

    private String principalTypeOf(Claims claims) {
        Object value = claims.get(PRINCIPAL_TYPE_KEY);
        return value == null ? null : value.toString();
    }

    private Long principalIdOf(Claims claims) {
        Object value = claims.get(PRINCIPAL_ID_KEY);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            log.warn("[JWT] 잘못된 principal id 클레임: {}", value);
            return null;
        }
    }

    /** 리프레시로 새 토큰을 낼 때 원래 토큰의 신원을 그대로 옮기기 위해 꺼낸다 */
    public String getPrincipalType(String token) {
        return principalTypeOf(parseClaims(token));
    }

    public Long getPrincipalId(String token) {
        return principalIdOf(parseClaims(token));
    }

    //토큰 정보를 검증하는 메서드
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT Token", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT Token", e);
            throw e;
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT Token", e);
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty.", e);
        }
        return false;
    }

    // 토큰 타입이 refresh인지 검증
    public boolean isRefreshToken(String token) {
        Claims claims = parseClaims(token);
        return TYPE_REFRESH.equals(claims.get("type"));
    }

    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(accessToken).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public String resolveToken(HttpServletRequest request) {

        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_TYPE)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    //JWT 토큰에서 사용자명(subject)을 추출하는 메서드
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Failed to extract username from token", e);
            throw new CustomException("토큰에서 사용자명을 추출하는 데 실패했습니다.", HttpStatus.BAD_REQUEST);
        }
    }
}