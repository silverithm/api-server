package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ApprovalLineEntryDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalViewerEntryDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplateViewer;
import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.DocumentNumberCounterRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.PositionRepository;
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
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;

/**
 * 기본 열람자가 있는 양식의 임시저장을 다시 저장하거나 상신할 때 터지지 않는지 지킨다.
 *
 * 실제로 있었던 사고(2026-10-09 버그제보방) — "수급자 퇴소 체크리스트를 임시저장한 뒤 결재를 제출하면
 * 제출이 안 되고 오류가 뜬다". 이 양식에는 기본 열람자(직책 3개)가 있다. 임시저장 문서를 고쳐 저장하거나
 * 상신하면 열람자를 통째로 지우고 다시 넣었는데, Hibernate가 INSERT를 DELETE보다 먼저 내보내
 * (approval_request_id, viewer_type, ref_id) 유니크 키에 걸렸다. 운영 DB에는 그날 같은 사람의
 * 임시저장만 세 건 남고, 결국 처음부터 새로 써서 올린 문서만 상신됐다.
 *
 * 양식 쪽 같은 사고는 {@link ApprovalTemplateViewerUpdateTest}가 지킨다. 여기도 진짜 DB(H2)에
 * 유니크 제약을 걸고 돌린다 — 저장소를 흉내로 바꾸면 제약이 없어 사고가 재현되지 않는다.
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
        "spring.datasource.url=jdbc:h2:mem:draftresavetest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ApprovalDraftResaveTest {

    private static final Long 기안자 = 9L;
    private static final long 사회복지사 = 79L;
    private static final long 요양팀장 = 133L;
    private static final long 사무국장 = 135L;
    private static final long 간호팀장 = 140L;

    @Autowired private ApprovalRequestRepository requestRepository;
    @Autowired private ApprovalTemplateRepository templateRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentNumberCounterRepository docNumberCounterRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private EntityManager em;

    private ApprovalRequestService service;
    private Company company;
    private ApprovalTemplate template;
    private final UserDetails 기안자로그인 = User.withUsername("drafter").password("x").roles("USER").build();

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("숲속재활어르신재가복지센터", null, null));

        template = ApprovalTemplate.builder()
                .company(company).name("수급자 퇴소 체크리스트").category("기타")
                .templateType("form").formSchema("{\"fields\":[],\"version\":1}")
                .build();
        for (long refId : List.of(사회복지사, 요양팀장, 사무국장)) {
            template.getDefaultViewers().add(ApprovalTemplateViewer.builder()
                    .template(template).viewerType(ApprovalViewerType.POSITION)
                    .refId(refId).viewerName("직책" + refId).build());
        }
        template = templateRepository.save(template);
        실제로저장한다();

        ApprovalAccessService accessService = mock(ApprovalAccessService.class);
        when(accessService.resolveCaller(any())).thenReturn(new ApprovalAccessService.CallerIdentity(
                ApprovalStep.ApproverType.MEMBER, 기안자, "이수나", company.getId(), String.valueOf(기안자)));
        when(accessService.resolveApprover(eq(ApprovalStep.ApproverType.MEMBER), anyLong(), any()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(1);
                    return new ApprovalAccessService.ResolvedApprover(
                            ApprovalStep.ApproverType.MEMBER, id, "결재자" + id, String.valueOf(id));
                });

        ApprovalViewerResolver viewerResolver = mock(ApprovalViewerResolver.class);
        when(viewerResolver.resolveName(any(), anyLong(), any())).thenAnswer(inv -> "직책" + inv.getArgument(1));

        service = new ApprovalRequestService(
                requestRepository, templateRepository, companyRepository,
                mock(FileStorageService.class), mock(NotificationService.class), mock(AdminNotificationTargets.class),
                memberRepository, userRepository, accessService, docNumberCounterRepository,
                mock(ResourceScopeGuard.class), viewerResolver, positionRepository);
    }

    /** 저장이 실제로 DB까지 내려가야 유니크 제약이 터진다 — 안 하면 사고를 놓친다 */
    private void 실제로저장한다() {
        em.flush();
        em.clear();
    }

    private static ApprovalLineEntryDTO 직원(long id) {
        ApprovalLineEntryDTO dto = new ApprovalLineEntryDTO();
        dto.setApproverType("MEMBER");
        dto.setApproverId(id);
        return dto;
    }

    private static ApprovalViewerEntryDTO 직책(long refId) {
        ApprovalViewerEntryDTO dto = new ApprovalViewerEntryDTO();
        dto.setViewerType(ApprovalViewerType.POSITION);
        dto.setRefId(refId);
        return dto;
    }

    /** 앱은 열람자를 보내지 않는다(null → 양식 기본값). 웹은 화면에 보이는 목록을 그대로 보낸다. */
    private CreateApprovalRequestDTO 문서(List<ApprovalViewerEntryDTO> viewers, boolean draft) {
        CreateApprovalRequestDTO dto = new CreateApprovalRequestDTO();
        dto.setTemplateId(template.getId());
        dto.setTitle("수급자 퇴소 체크리스트 — 김OO");
        dto.setFormData("{\"reason\":\"입원\"}");
        dto.setApprovalLine(List.of(직원(7L), 직원(1L)));
        dto.setViewers(viewers);
        dto.setDraft(draft);
        return dto;
    }

    private Long 임시저장한다() {
        ApprovalRequestDTO saved = service.createApprovalRequest(
                company.getId(), String.valueOf(기안자), "이수나", 문서(null, true));
        실제로저장한다();
        return saved.getId();
    }

    private List<Long> 열람자번호(Long id) {
        return requestRepository.findById(id).orElseThrow().getViewers().stream()
                .map(v -> v.getRefId()).sorted().toList();
    }

    @Test
    @DisplayName("사고 재현: 앱에서 임시저장한 퇴소 체크리스트를 그대로 상신해도 터지지 않는다")
    void 앱_임시저장을_상신한다() {
        Long id = 임시저장한다();

        assertThatCode(() -> {
            service.submitDraft(id, 기안자로그인, 문서(null, false));
            실제로저장한다();
        }).doesNotThrowAnyException();

        ApprovalRequest submitted = requestRepository.findById(id).orElseThrow();
        assertThat(submitted.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(submitted.getSteps()).hasSize(2);
        assertThat(열람자번호(id)).containsExactly(사회복지사, 요양팀장, 사무국장);
    }

    @Test
    @DisplayName("웹처럼 열람자 목록을 그대로 보내 상신해도 터지지 않는다")
    void 웹_임시저장을_상신한다() {
        Long id = 임시저장한다();

        assertThatCode(() -> {
            service.submitDraft(id, 기안자로그인, 문서(List.of(직책(사회복지사), 직책(요양팀장), 직책(사무국장)), false));
            실제로저장한다();
        }).doesNotThrowAnyException();

        assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("임시저장을 여러 번 고쳐 저장한 뒤 상신해도 된다")
    void 여러번_이어쓴_뒤_상신한다() {
        Long id = 임시저장한다();

        assertThatCode(() -> {
            for (int i = 0; i < 3; i++) {
                service.updateDraft(id, 기안자로그인, 문서(null, true));
                실제로저장한다();
            }
            service.submitDraft(id, 기안자로그인, 문서(null, false));
            실제로저장한다();
        }).doesNotThrowAnyException();

        assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(열람자번호(id)).containsExactly(사회복지사, 요양팀장, 사무국장);
    }

    @Test
    @DisplayName("이어쓰면서 열람자를 하나 빼고 하나 더해도 그대로 반영된다")
    void 열람자를_바꿔_이어쓴다() {
        Long id = 임시저장한다();

        service.updateDraft(id, 기안자로그인, 문서(List.of(직책(사회복지사), 직책(간호팀장)), true));
        실제로저장한다();

        assertThat(열람자번호(id)).containsExactly(사회복지사, 간호팀장);
    }

    @Test
    @DisplayName("빈 목록으로 이어쓰면 열람 지정이 모두 빠진다")
    void 열람자를_모두_뺀다() {
        Long id = 임시저장한다();

        service.updateDraft(id, 기안자로그인, 문서(List.of(), true));
        실제로저장한다();

        assertThat(열람자번호(id)).isEmpty();
    }
}
