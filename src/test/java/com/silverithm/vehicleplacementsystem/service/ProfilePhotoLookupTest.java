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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 사람 식별자 여럿을 받아 프로필 사진을 한 번에 찾아 주는 부품을 지킨다.
 *
 * 여기서 틀리면 공지 읽은 사람·댓글에서 사진이 통째로 안 나오거나,
 * 더 나쁘게는 **관리자 3번의 사진이 직원 3번에게 붙는다**.
 * 직원과 관리자는 서로 다른 표라 번호가 겹치기 때문이다.
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
        "spring.datasource.url=jdbc:h2:mem:photolookup;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ProfilePhotoLookupTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;

    private ProfilePhotoLookup lookup;
    private Company company;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.of("숲속재활어르신재가복지센터", "서울", null));
        lookup = new ProfilePhotoLookup(memberRepository, userRepository);
    }

    private Member 직원(String name, String photo) {
        return memberRepository.save(Member.builder()
                .name(name).username(name + "@t.local").email(name + "@t.local")
                .password("x").role(Member.Role.CAREGIVER).status(Member.MemberStatus.ACTIVE)
                .profileImageUrl(photo).company(company).build());
    }

    private AppUser 관리자(String name, String photo) {
        AppUser u = userRepository.save(
                new AppUser(name, name + "@a.local", "x", null, null, company, null));
        u.updateProfileImageUrl(photo);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("직원과 관리자의 사진을 한 번에 찾아 준다")
    void 둘다찾는다() {
        Member m = 직원("직원A", "https://img/staff.jpg");
        AppUser a = 관리자("관리자B", "https://img/admin.jpg");

        Map<String, String> photos = lookup.photosOf(
                List.of(String.valueOf(m.getId()), "admin_" + a.getId()));

        assertThat(photos).containsEntry(String.valueOf(m.getId()), "https://img/staff.jpg");
        assertThat(photos).containsEntry("admin_" + a.getId(), "https://img/admin.jpg");
    }

    @Test
    @DisplayName("번호가 겹쳐도 직원과 관리자를 섞지 않는다")
    void 번호가겹쳐도안섞인다() {
        Member m = 직원("직원", "https://img/STAFF.jpg");
        AppUser a = 관리자("관리자", "https://img/ADMIN.jpg");

        // 두 표의 번호가 우연히 같은 상황을 만든다 — 접두사가 없으면 구별할 수 없다
        Map<String, String> photos = lookup.photosOf(
                List.of(String.valueOf(m.getId()), "admin_" + a.getId()));

        assertThat(photos.get(String.valueOf(m.getId()))).isEqualTo("https://img/STAFF.jpg");
        assertThat(photos.get("admin_" + a.getId())).isEqualTo("https://img/ADMIN.jpg");
    }

    @Test
    @DisplayName("사진이 없는 사람은 아예 빠진다 — 화면이 이름 첫 글자로 그린다")
    void 사진없으면빠진다() {
        Member m = 직원("무사진", null);
        assertThat(lookup.photosOf(List.of(String.valueOf(m.getId())))).isEmpty();
    }

    @Test
    @DisplayName("없는 사람 번호를 줘도 터지지 않는다")
    void 없는사람() {
        assertThat(lookup.photosOf(List.of("999999", "admin_999999"))).isEmpty();
    }

    @Test
    @DisplayName("빈 목록·null·빈 문자열을 줘도 터지지 않는다")
    void 빈입력() {
        assertThat(lookup.photosOf(List.of())).isEmpty();
        assertThat(lookup.photosOf(java.util.Arrays.asList(null, "", "   "))).isEmpty();
    }

    @Test
    @DisplayName("같은 사람을 여러 번 줘도 한 번만 찾는다")
    void 중복입력() {
        Member m = 직원("중복", "https://img/dup.jpg");
        String id = String.valueOf(m.getId());

        Map<String, String> photos = lookup.photosOf(List.of(id, id, id));

        assertThat(photos).hasSize(1).containsEntry(id, "https://img/dup.jpg");
    }

    @Test
    @DisplayName("숫자가 아닌 식별자가 섞여 있어도 나머지는 정상으로 찾는다")
    void 이상한값이섞여도() {
        Member m = 직원("정상", "https://img/ok.jpg");

        Map<String, String> photos = lookup.photosOf(
                List.of(String.valueOf(m.getId()), "이메일@형식.com", "admin_", "abc"));

        assertThat(photos).containsEntry(String.valueOf(m.getId()), "https://img/ok.jpg");
    }
}
