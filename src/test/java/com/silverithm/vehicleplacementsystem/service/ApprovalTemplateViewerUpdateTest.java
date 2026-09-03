package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ApprovalTemplateDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalViewerEntryDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalTemplateRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
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
 * 결재 양식의 열람 대상을 고쳐 저장할 때 터지지 않는지 지킨다.
 *
 * 실제로 있었던 사고 — "수급자 퇴소 체크리스트"를 열어 아무것도 안 바꾸고 저장만 눌렀는데
 * 화면에 "저장 실패 — 백엔드 서버 오류: 500"이 떴다. 서버 로그:
 *
 * <pre>
 * Duplicate entry '217-POSITION-79' for key 'approval_template_viewers.uk_approval_template_viewers'
 * </pre>
 *
 * 원인은 열람자를 통째로 지우고 다시 넣은 것이었다. 한 번의 flush 안에서 Hibernate가
 * INSERT를 DELETE보다 먼저 내보내므로, 그대로 유지되는 열람자가 하나라도 있으면
 * (template_id, viewer_type, ref_id) 유니크 키에 걸린다.
 *
 * 그래서 이 테스트는 **진짜 DB(H2)에 유니크 제약을 걸고** 돌린다 —
 * 저장소를 흉내로 바꾸면 제약이 없어 사고가 재현되지 않는다.
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
        "spring.datasource.url=jdbc:h2:mem:tmplviewertest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ApprovalTemplateViewerUpdateTest {

    @Autowired
    private ApprovalTemplateRepository templateRepository;
    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private EntityManager em;

    private ApprovalTemplateService service;
    private ApprovalViewerResolver resolver;
    private Long companyId;

    /** 직책 번호 → 이름. 실제 이름 조회는 이 테스트의 관심사가 아니라 흉내로 둔다. */
    private static final long 사회복지사 = 79L;
    private static final long 사무국장 = 80L;
    private static final long 사무팀장 = 81L;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(new Company("숲속재활어르신재가복지센터", null, null));
        companyId = company.getId();

        ResourceScopeGuard guard = mock(ResourceScopeGuard.class);
        doNothing().when(guard).requireSameCompany(any(Company.class));

        resolver = mock(ApprovalViewerResolver.class);
        when(resolver.resolveName(any(), anyLong(), any())).thenAnswer(inv -> "대상" + inv.getArgument(1));

        service = new ApprovalTemplateService(
                templateRepository, approvalRequestRepository, companyRepository, guard, resolver);
    }

    private static ApprovalViewerEntryDTO 직책(long refId) {
        ApprovalViewerEntryDTO dto = new ApprovalViewerEntryDTO();
        dto.setViewerType(ApprovalViewerType.POSITION);
        dto.setRefId(refId);
        return dto;
    }

    private CreateApprovalTemplateRequestDTO 요청(List<ApprovalViewerEntryDTO> viewers) {
        CreateApprovalTemplateRequestDTO req = new CreateApprovalTemplateRequestDTO();
        req.setName("수급자 퇴소 체크리스트");
        req.setCategory("기타");
        req.setTemplateType("form");
        req.setFormSchema("{\"fields\":[],\"version\":1}");
        req.setDefaultViewers(viewers);
        return req;
    }

    /** 저장이 실제로 DB까지 내려가야 유니크 제약이 터진다 — 안 하면 사고를 놓친다 */
    private void 실제로저장한다() {
        em.flush();
        em.clear();
    }

    private List<Long> 열람자번호(Long templateId) {
        return templateRepository.findById(templateId).orElseThrow()
                .getDefaultViewers().stream()
                .map(v -> v.getRefId())
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("사고 재현: 열람자를 그대로 두고 저장만 눌러도 터지지 않는다")
    void 그대로저장해도터지지않는다() {
        ApprovalTemplateDTO created = service.createTemplate(
                companyId, 요청(List.of(직책(사회복지사), 직책(사무국장), 직책(사무팀장))));
        실제로저장한다();

        // 사장님이 실제로 한 것 — 아무것도 안 바꾸고 저장
        assertThatCode(() -> {
            service.updateTemplate(created.getId(), 요청(List.of(직책(사회복지사), 직책(사무국장), 직책(사무팀장))));
            실제로저장한다();
        }).doesNotThrowAnyException();

        assertThat(열람자번호(created.getId())).containsExactly(사회복지사, 사무국장, 사무팀장);
    }

    @Test
    @DisplayName("한 명만 빼도 나머지는 그대로 남는다")
    void 한명만뺀다() {
        ApprovalTemplateDTO created = service.createTemplate(
                companyId, 요청(List.of(직책(사회복지사), 직책(사무국장), 직책(사무팀장))));
        실제로저장한다();

        service.updateTemplate(created.getId(), 요청(List.of(직책(사회복지사), 직책(사무팀장))));
        실제로저장한다();

        assertThat(열람자번호(created.getId())).containsExactly(사회복지사, 사무팀장);
    }

    @Test
    @DisplayName("한 명만 더해도 기존 사람이 중복으로 들어가지 않는다")
    void 한명만더한다() {
        ApprovalTemplateDTO created = service.createTemplate(companyId, 요청(List.of(직책(사회복지사))));
        실제로저장한다();

        assertThatCode(() -> {
            service.updateTemplate(created.getId(), 요청(List.of(직책(사회복지사), 직책(사무국장))));
            실제로저장한다();
        }).doesNotThrowAnyException();

        assertThat(열람자번호(created.getId())).containsExactly(사회복지사, 사무국장);
    }

    @Test
    @DisplayName("한 명 빼고 한 명 더하기를 한 번에 해도 된다")
    void 빼고더하기를한번에() {
        ApprovalTemplateDTO created = service.createTemplate(
                companyId, 요청(List.of(직책(사회복지사), 직책(사무국장))));
        실제로저장한다();

        service.updateTemplate(created.getId(), 요청(List.of(직책(사회복지사), 직책(사무팀장))));
        실제로저장한다();

        assertThat(열람자번호(created.getId())).containsExactly(사회복지사, 사무팀장);
    }

    @Test
    @DisplayName("빈 목록으로 저장하면 열람 지정이 모두 지워진다")
    void 전부지운다() {
        ApprovalTemplateDTO created = service.createTemplate(
                companyId, 요청(List.of(직책(사회복지사), 직책(사무국장))));
        실제로저장한다();

        service.updateTemplate(created.getId(), 요청(List.of()));
        실제로저장한다();

        assertThat(열람자번호(created.getId())).isEmpty();
    }

    @Test
    @DisplayName("같은 사람을 두 번 보내도 한 번만 저장된다")
    void 중복입력은한번만() {
        ApprovalTemplateDTO created = service.createTemplate(
                companyId, 요청(List.of(직책(사회복지사), 직책(사회복지사), 직책(사무국장))));
        실제로저장한다();

        assertThat(열람자번호(created.getId())).containsExactly(사회복지사, 사무국장);
    }

    @Test
    @DisplayName("열람자를 안 보내면(null) 기존 지정을 건드리지 않는다")
    void null이면그대로둔다() {
        ApprovalTemplateDTO created = service.createTemplate(
                companyId, 요청(List.of(직책(사회복지사), 직책(사무국장))));
        실제로저장한다();

        CreateApprovalTemplateRequestDTO 열람자없는요청 = 요청(null);
        service.updateTemplate(created.getId(), 열람자없는요청);
        실제로저장한다();

        assertThat(열람자번호(created.getId())).containsExactly(사회복지사, 사무국장);
    }

    @Test
    @DisplayName("직책 이름이 바뀌면 저장할 때 열람자 이름도 따라 바뀐다")
    void 이름이바뀌면따라간다() {
        ApprovalTemplateDTO created = service.createTemplate(companyId, 요청(List.of(직책(사회복지사))));
        실제로저장한다();

        // 직책 이름을 바꾼 상황 — 이름 조회 결과가 달라진다
        when(resolver.resolveName(any(), anyLong(), any())).thenReturn("사회복지사(선임)");

        service.updateTemplate(created.getId(), 요청(List.of(직책(사회복지사))));
        실제로저장한다();

        assertThat(templateRepository.findById(created.getId()).orElseThrow()
                .getDefaultViewers())
                .extracting(v -> v.getViewerName())
                .containsExactly("사회복지사(선임)");
    }

    @Test
    @DisplayName("연달아 여러 번 저장해도 매번 통과한다")
    void 여러번저장해도된다() {
        ApprovalTemplateDTO created = service.createTemplate(
                companyId, 요청(List.of(직책(사회복지사), 직책(사무국장))));
        실제로저장한다();

        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                service.updateTemplate(created.getId(), 요청(List.of(직책(사회복지사), 직책(사무국장))));
                실제로저장한다();
            }
        }).doesNotThrowAnyException();

        assertThat(열람자번호(created.getId())).containsExactly(사회복지사, 사무국장);
    }
}
