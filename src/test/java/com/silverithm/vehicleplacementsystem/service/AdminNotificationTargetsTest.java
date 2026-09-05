package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 관리자에게 보내는 알림에 "누구에게 보낸 것인지"가 남는지 지킨다.
 *
 * 예전에는 전부 recipientUserId="admin"이라는 리터럴로 저장했다. 앱은 자기 id로 알림함을
 * 조회하므로 그 알림들은 아무에게도 보이지 않았고(푸시만 도착), "admin"에는 기관 구분이
 * 없어 여러 기관의 알림이 한 바구니에 쌓였다.
 */
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:admintargets;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class AdminNotificationTargetsTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EntityManager em;

    private AdminNotificationTargets targets;
    private Company company;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.of("햇살요양원", "서울", null));
        targets = new AdminNotificationTargets(memberRepository);
    }

    private AppUser 가입계정관리자(String name, String token) {
        AppUser user = userRepository.save(
                new AppUser(name, name + "@a.local", "x", null, null, company, null));
        user.updateFcmToken(token);
        return userRepository.save(user);
    }

    private Member 직원(String name, Member.Role role, String token) {
        return memberRepository.save(Member.builder()
                .name(name).username(name + "@t.local").email(name + "@t.local")
                .password("x").role(role).status(Member.MemberStatus.ACTIVE)
                .fcmToken(token).company(company).build());
    }

    private List<AdminNotificationTargets.AdminRecipient> 대상() {
        em.flush();
        em.clear();
        return targets.recipientsOf(companyRepository.findById(company.getId()).orElseThrow());
    }

    @Test
    @DisplayName("가입 계정 관리자는 채팅과 같은 규약(admin_<id>)으로 남는다")
    void 가입계정관리자는접두사가붙는다() {
        AppUser admin = 가입계정관리자("원장", "token-admin");

        assertThat(대상())
                .extracting(AdminNotificationTargets.AdminRecipient::userId)
                .containsExactly("admin_" + admin.getId());
    }

    @Test
    @DisplayName("ADMIN 역할 직원은 원시 id로 남는다 — 관리자와 표가 달라 번호가 겹친다")
    void 직원관리자는원시id로남는다() {
        Member manager = 직원("사무국장", Member.Role.ADMIN, "token-member");

        assertThat(대상())
                .extracting(AdminNotificationTargets.AdminRecipient::userId)
                .containsExactly(String.valueOf(manager.getId()));
    }

    @Test
    @DisplayName("받는 사람 이름도 함께 남는다 — 알림함에 '관리자'라고만 뜨지 않게")
    void 이름이남는다() {
        직원("사무국장", Member.Role.ADMIN, "token-member");

        assertThat(대상())
                .extracting(AdminNotificationTargets.AdminRecipient::userName)
                .containsExactly("사무국장");
    }

    @Test
    @DisplayName("관리자가 아닌 직원에게는 가지 않는다")
    void 요양보호사는대상이아니다() {
        직원("요양보호사", Member.Role.CAREGIVER, "token-caregiver");

        assertThat(대상()).isEmpty();
    }

    @Test
    @DisplayName("토큰이 같으면(한 기기에 두 계정) 한 번만 보낸다")
    void 같은기기는한번만() {
        가입계정관리자("원장", "same-token");
        직원("사무국장", Member.Role.ADMIN, "same-token");

        assertThat(대상()).hasSize(1);
    }

    @Test
    @DisplayName("토큰이 없는 관리자는 대상에서 빠진다")
    void 토큰없으면제외() {
        가입계정관리자("알림끔", null);

        assertThat(대상()).isEmpty();
    }
}
