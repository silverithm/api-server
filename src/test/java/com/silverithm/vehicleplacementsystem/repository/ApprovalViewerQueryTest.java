package com.silverithm.vehicleplacementsystem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 결재함 조회 쿼리가 실제로 파싱·실행되는지 검증한다.
 * 열람 조건(EXISTS 서브쿼리 2개)과 확장 검색 조건은 JPQL이라 컴파일로 걸러지지 않는다.
 */
@DataJpaTest
@Import(QuerydslConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:approvalviewertest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=WARN"
})
class ApprovalViewerQueryTest {

    @Autowired
    private ApprovalRequestRepository requestRepository;

    @Test
    @DisplayName("열람 범위 목록 검색이 필터 조합마다 실행된다")
    void searchViewableRuns() {
        var start = LocalDate.now().minusMonths(1).atStartOfDay();
        var end = LocalDate.now().atTime(LocalTime.MAX);

        // 관리자 — 필터 없음
        assertThat(requestRepository.searchViewable(1L, null, start, end, null, null, null,
                true, "admin_1", ApprovalStep.ApproverType.ADMIN, ApprovalViewerType.ADMIN, 1L, -1L)).isEmpty();

        // 직원 — 상태·양식·대분류·검색어를 모두 건 경우
        assertThat(requestRepository.searchViewable(1L, ApprovalStatus.PENDING, start, end, 3L, "회의", "회의록",
                false, "7", ApprovalStep.ApproverType.MEMBER, ApprovalViewerType.MEMBER, 7L, 2L)).isEmpty();

        // 미분류만 보기
        assertThat(requestRepository.searchViewable(1L, null, start, end, null, "__NONE__", null,
                false, "7", ApprovalStep.ApproverType.MEMBER, ApprovalViewerType.MEMBER, 7L, -1L)).isEmpty();
    }

    @Test
    @DisplayName("열람 범위 상태별 집계가 실행된다")
    void countViewableByStatusRuns() {
        assertThat(requestRepository.countViewableByStatus(1L, false, "7",
                ApprovalStep.ApproverType.MEMBER, ApprovalViewerType.MEMBER, 7L, 2L)).isEmpty();
    }
}
