package com.silverithm.vehicleplacementsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.jwt.CarevPrincipal;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.silverithm.vehicleplacementsystem.service.TokenIdentityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 토큰의 주인 찾기 — **직원도 찾을 수 있어야 한다.**
 *
 * 관리자와 직원은 서로 다른 표(app_user / members)에 산다. 토큰 검증이 관리자 표만
 * 뒤지는 바람에 직원이 낸 토큰은 늘 "존재하지 않는 사용자"로 판정됐고, 앱은 그 답을 받아
 * 토큰을 갱신한 뒤 다시 물었다가 또 무효를 듣고 로그인 화면으로 돌아갔다.
 * "모바일 케어브이 새로 깔아도 자동로그인이 유지가 안 된다"는 제보가 이것이다.
 */
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class, TokenIdentityResolver.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:tokenidentity;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class TokenIdentityResolverTest {

    @Autowired
    private TokenIdentityResolver resolver;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member 직원;

    @BeforeEach
    void setUp() {
        AppUser admin = new AppUser("김도형", "admin@carev.kr", "x",
                com.silverithm.vehicleplacementsystem.entity.UserRole.ROLE_ADMIN, null, null, null);
        userRepository.save(admin);

        직원 = Member.builder()
                .username("bjm")
                .name("배정민")
                .email("bjm@carev.kr")
                .password("x")
                .role(Member.Role.CAREGIVER)
                .status(Member.MemberStatus.ACTIVE)
                .build();
        memberRepository.save(직원);
    }

    @Test
    @DisplayName("직원 토큰의 주인을 찾는다 — 여기서 자동로그인이 풀리고 있었다")
    void findsMember() {
        var identity = resolver.resolve(CarevPrincipal.TYPE_MEMBER, 직원.getId(), "bjm");

        assertThat(identity).isPresent();
        assertThat(identity.get().id()).isEqualTo(직원.getId());
        assertThat(identity.get().displayName()).isEqualTo("배정민");
        assertThat(identity.get().admin()).isFalse();
    }

    @Test
    @DisplayName("관리자 토큰은 예전처럼 관리자 표에서 찾는다")
    void findsAdmin() {
        var identity = resolver.resolve(CarevPrincipal.TYPE_ADMIN, 1L, "admin@carev.kr");

        assertThat(identity).isPresent();
        assertThat(identity.get().displayName()).isEqualTo("김도형");
        assertThat(identity.get().admin()).isTrue();
    }

    @Test
    @DisplayName("클레임이 없는 옛 토큰도 찾는다 — 아직 살아 있는 토큰이 있다")
    void findsWithoutClaims() {
        assertThat(resolver.resolve(null, null, "admin@carev.kr")).isPresent();
        assertThat(resolver.resolve(null, null, "bjm")).isPresent();
    }

    @Test
    @DisplayName("id 클레임이 틀어져도 아이디로 찾는다")
    void fallsBackToUsername() {
        var identity = resolver.resolve(CarevPrincipal.TYPE_MEMBER, 999999L, "bjm");

        assertThat(identity).isPresent();
        assertThat(identity.get().id()).isEqualTo(직원.getId());
    }

    @Test
    @DisplayName("탈퇴한 직원의 토큰은 살려 두지 않는다")
    void rejectsDeletedMember() {
        직원.setStatus(Member.MemberStatus.DELETED);
        memberRepository.save(직원);

        assertThat(resolver.resolve(CarevPrincipal.TYPE_MEMBER, 직원.getId(), "bjm")).isEmpty();
    }

    /**
     * **진짜 토큰으로** 한 번 더 본다.
     *
     * 위 시험들은 클레임 값을 손으로 넣어 준다. 실제로는 로그인이 발급한 토큰을 앱이 그대로
     * 돌려주고, 서버가 거기서 클레임을 꺼낸다. 발급과 해석 사이가 어긋나 있으면
     * 규칙이 아무리 맞아도 현장에서는 또 로그인 화면을 만난다.
     */
    @Test
    @DisplayName("로그인이 발급한 직원 토큰을 그대로 넣어도 주인을 찾는다")
    void resolvesRealMemberToken() {
        // 테스트 전용 키 — 운영 키와 무관하다
        JwtTokenProvider provider = new JwtTokenProvider(
                "dGVzdC1vbmx5LWtleS1mb3Itand0LXRva2VuLXByb3ZpZGVyLXRlc3RzLTEyMzQ1Ng==");

        String accessToken = provider.generateToken(
                직원.getUsername(),
                List.of(new SimpleGrantedAuthority("ROLE_CAREGIVER")),
                CarevPrincipal.TYPE_MEMBER,
                직원.getId()
        ).getAccessToken();

        var identity = resolver.resolve(
                provider.getPrincipalType(accessToken),
                provider.getPrincipalId(accessToken),
                io.jsonwebtoken.Jwts.parserBuilder()
                        .setSigningKey(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                io.jsonwebtoken.io.Decoders.BASE64.decode(
                                        "dGVzdC1vbmx5LWtleS1mb3Itand0LXRva2VuLXByb3ZpZGVyLXRlc3RzLTEyMzQ1Ng==")))
                        .build()
                        .parseClaimsJws(accessToken)
                        .getBody()
                        .getSubject()
        );

        assertThat(identity).isPresent();
        assertThat(identity.get().id()).isEqualTo(직원.getId());
        assertThat(identity.get().admin()).isFalse();
    }

    @Test
    @DisplayName("없는 사람은 못 찾는다")
    void rejectsUnknown() {
        assertThat(resolver.resolve(CarevPrincipal.TYPE_MEMBER, 12345L, "없는사람")).isEmpty();
        assertThat(resolver.resolve(CarevPrincipal.TYPE_ADMIN, 1L, "nobody@carev.kr")).isEmpty();
    }
}
