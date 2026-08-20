package com.silverithm.vehicleplacementsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import java.util.List;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 개인정보 접근 범위를 제한하는 쿼리들이 실제로 파싱·실행되는지 검증한다.
 * (JPQL 오타/잘못된 조인 경로는 컴파일이 아니라 런타임에만 드러나므로 필요)
 */
@DataJpaTest
// 엔티티에 걸린 PII 컨버터가 암호화 빈을 요구하므로 슬라이스 테스트에도 함께 올린다
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        // 테스트 전용 키 — 컨버터 빈을 만들기 위한 값일 뿐 실제 데이터를 다루지 않는다
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:scopetest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=WARN"
})
class CompanyScopeQueryTest {

    @Autowired
    private ElderRepository elderRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private FileOwnershipRepository fileOwnershipRepository;

    @Test
    @DisplayName("어르신 기관 범위 쿼리가 실행된다")
    void elderScopeQueriesRun() {
        assertThat(elderRepository.findAllInCompanyScope(1L)).isEmpty();
        assertThat(elderRepository.existsInCompanyScope(1L, 1L)).isFalse();
    }

    @Test
    @DisplayName("직원 기관 범위 쿼리가 실행된다")
    void employeeScopeQueriesRun() {
        assertThat(employeeRepository.findAllInCompanyScope(1L)).isEmpty();
        assertThat(employeeRepository.existsInCompanyScope(1L, 1L)).isFalse();
    }

    @Test
    @DisplayName("파일 귀속 검증 쿼리 8종이 모두 실행된다")
    void fileOwnershipQueriesRun() {
        // 상대 경로와 절대 S3 URL 두 표기를 함께 대조한다
        List<String> paths = List.of(
                "approvals/sample.pdf",
                "https://bucket.s3.ap-northeast-2.amazonaws.com/carev/approvals/sample.pdf");

        assertThat(fileOwnershipRepository.existsApprovalAttachment(1L, paths)).isFalse();
        assertThat(fileOwnershipRepository.existsApprovalExtraAttachment(1L, paths)).isFalse();
        assertThat(fileOwnershipRepository.existsTemplateFile(1L, paths)).isFalse();
        assertThat(fileOwnershipRepository.existsApprovalStepSignature(1L, paths)).isFalse();
        assertThat(fileOwnershipRepository.existsChatFile(1L, paths)).isFalse();
        assertThat(fileOwnershipRepository.existsAdminSignature(1L, paths)).isFalse();
        assertThat(fileOwnershipRepository.existsMemberSignature(1L, paths)).isFalse();
        assertThat(fileOwnershipRepository.existsCompanySeal(1L, paths)).isFalse();
    }
}
