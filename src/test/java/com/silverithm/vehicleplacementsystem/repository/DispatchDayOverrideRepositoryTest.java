package com.silverithm.vehicleplacementsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.DispatchDayOverride;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 그날 하루치 배차 수정본은 **기관별·날짜별로 한 벌**이어야 한다.
 *
 * 두 벌이 생기면 같은 날 배차표가 누가 저장했느냐에 따라 달라진다 — 현장에서 가장 나쁜 실패다.
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
        "spring.datasource.url=jdbc:h2:mem:dispatchoverride;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class DispatchDayOverrideRepositoryTest {

    @Autowired
    private DispatchDayOverrideRepository repository;

    private static final LocalDate 금요일 = LocalDate.of(2026, 9, 11);

    private DispatchDayOverride 수정본(Long companyId, LocalDate date, String json) {
        return DispatchDayOverride.builder()
                .companyId(companyId)
                .dispatchDate(date)
                .overridesJson(json)
                .build();
    }

    @Test
    @DisplayName("그날 수정본을 기관·날짜로 찾는다")
    void findsByCompanyAndDate() {
        repository.save(수정본(4L, 금요일, "[{\"seniorId\":\"s1\",\"routeId\":\"r2\",\"boardingOrder\":1}]"));

        assertThat(repository.findByCompanyIdAndDispatchDate(4L, 금요일))
                .isPresent()
                .get()
                .extracting(DispatchDayOverride::getOverridesJson)
                .asString()
                .contains("\"routeId\":\"r2\"");
    }

    @Test
    @DisplayName("다른 기관의 같은 날짜는 섞이지 않는다")
    void doesNotLeakAcrossCompanies() {
        repository.save(수정본(4L, 금요일, "[{\"seniorId\":\"우리\"}]"));
        repository.save(수정본(5L, 금요일, "[{\"seniorId\":\"남의기관\"}]"));

        assertThat(repository.findByCompanyIdAndDispatchDate(4L, 금요일).orElseThrow().getOverridesJson())
                .contains("우리")
                .doesNotContain("남의기관");
    }

    @Test
    @DisplayName("수정한 적 없는 날은 비어 있다 — 화면은 설정대로 그린다")
    void emptyWhenNeverEdited() {
        assertThat(repository.findByCompanyIdAndDispatchDate(4L, 금요일.plusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("한 달치를 한 번에 읽는다 — 달력이 하루씩 서른 번 묻지 않게")
    void readsARange() {
        repository.save(수정본(4L, 금요일, "[{\"seniorId\":\"a\"}]"));
        repository.save(수정본(4L, 금요일.plusDays(3), "[{\"seniorId\":\"b\"}]"));
        repository.save(수정본(4L, 금요일.plusMonths(2), "[{\"seniorId\":\"멀리\"}]"));

        List<DispatchDayOverride> rows = repository.findByCompanyIdAndDispatchDateBetween(
                4L, 금요일.withDayOfMonth(1), 금요일.withDayOfMonth(30));

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.getOverridesJson()).doesNotContain("멀리"));
    }
}
