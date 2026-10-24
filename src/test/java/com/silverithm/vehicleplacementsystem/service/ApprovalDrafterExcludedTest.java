package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 기안자가 결재선에 들어 있으면 자동으로 빠진다.
 *
 * 실제로 있었던 제보(2026-10-24) — "이수나 국장이 기안을 작성하는데 결재라인에 이수나 국장이 있으면
 * 빠져야 되는데, 이수나 국장 결재를 또 해야 되는 상황이 있다". 양식의 기본 결재선을 그대로 쓰기 때문에
 * 그 결재선에 든 사람이 직접 기안하면 자기 문서를 자기가 다시 결재했다.
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
        "spring.datasource.url=jdbc:h2:mem:drafterexcludetest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ApprovalDrafterExcludedTest {

    private static final long 기안자 = 9L;
    private static final long 팀장 = 21L;
    private static final long 원장 = 22L;

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
        template = templateRepository.save(ApprovalTemplate.builder()
                .company(company).name("지출결의서").category("기타")
                .templateType("form").formSchema("{\"fields\":[],\"version\":1}")
                .build());
        em.flush();
        em.clear();

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

        service = new ApprovalRequestService(
                requestRepository, templateRepository, companyRepository,
                mock(FileStorageService.class), mock(NotificationService.class), mock(AdminNotificationTargets.class),
                memberRepository, userRepository, accessService, docNumberCounterRepository,
                mock(ResourceScopeGuard.class), viewerResolver, positionRepository);
    }

    private static ApprovalLineEntryDTO 직원(long id) {
        ApprovalLineEntryDTO dto = new ApprovalLineEntryDTO();
        dto.setApproverType("MEMBER");
        dto.setApproverId(id);
        return dto;
    }

    private CreateApprovalRequestDTO 기안(boolean 임시저장, long... 결재선) {
        CreateApprovalRequestDTO dto = new CreateApprovalRequestDTO();
        dto.setTemplateId(template.getId());
        dto.setTitle("지출결의서");
        dto.setFormData("{}");
        dto.setApprovalLine(java.util.Arrays.stream(결재선).mapToObj(ApprovalDrafterExcludedTest::직원).toList());
        dto.setDraft(임시저장);
        return dto;
    }

    private static List<Long> 결재자들(ApprovalRequestDTO dto) {
        return dto.getApprovalLine().stream().map(s -> Long.valueOf(s.getApproverId())).toList();
    }

    // 결재선 가운데 낀 기안자는 빠지고, 남은 사람 기준으로 번호와 최종 결재자가 다시 정해진다
    @Test
    @DisplayName("기안자가 결재선 중간에 있으면 빠지고 번호·최종 결재자를 다시 매긴다")
    void drafterInTheMiddleIsDropped() {
        ApprovalRequestDTO result = service.createApprovalRequest(
                company.getId(), String.valueOf(기안자), "이수나", 기안(false, 팀장, 기안자, 원장));

        assertThat(결재자들(result)).containsExactly(팀장, 원장);
        assertThat(result.getApprovalLine()).extracting(s -> s.getStepOrder()).containsExactly(1, 2);
        assertThat(result.getApprovalLine().get(1).getRoleLabel()).isEqualTo("FINAL");
        assertThat(result.getApprovalLine().get(0).getRoleLabel()).isEqualTo("REVIEWER");
    }

    // 마지막 결재자가 기안자면 그 앞 사람이 최종 결재자가 된다 — 국장이 최종인 양식이 보통이다
    @Test
    @DisplayName("기안자가 최종 결재자였으면 앞 사람이 최종 결재자가 된다")
    void drafterAsFinalApproverHandsOverFinal() {
        ApprovalRequestDTO result = service.createApprovalRequest(
                company.getId(), String.valueOf(기안자), "이수나", 기안(false, 팀장, 기안자));

        assertThat(결재자들(result)).containsExactly(팀장);
        assertThat(result.getApprovalLine().get(0).getRoleLabel()).isEqualTo("FINAL");
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    // 결재선에 기안자 혼자뿐이면 결재선 없는 문서가 된다(관리자 단일 승인) — 자기 결재 단계가 남지 않는다
    @Test
    @DisplayName("기안자 혼자뿐인 결재선은 결재선 없는 문서가 된다")
    void drafterAloneLeavesNoLine() {
        ApprovalRequestDTO result = service.createApprovalRequest(
                company.getId(), String.valueOf(기안자), "이수나", 기안(false, 기안자));

        assertThat(result.getApprovalLine()).isEmpty();
        assertThat(result.getHasApprovalLine()).isFalse();
    }

    // 기안자가 아닌 사람들의 결재선은 건드리지 않는다
    @Test
    @DisplayName("기안자가 없는 결재선은 그대로 둔다")
    void lineWithoutDrafterIsUntouched() {
        ApprovalRequestDTO result = service.createApprovalRequest(
                company.getId(), String.valueOf(기안자), "이수나", 기안(false, 팀장, 원장));

        assertThat(결재자들(result)).containsExactly(팀장, 원장);
        assertThat(result.getApprovalLine().get(1).getRoleLabel()).isEqualTo("FINAL");
    }

    // 임시저장해 두었다가 상신하는 길로 들어와도 똑같이 빠진다
    @Test
    @DisplayName("임시저장 뒤 상신해도 기안자는 빠진다")
    void drafterIsAlsoDroppedWhenSubmittingADraft() {
        ApprovalRequestDTO draft = service.createApprovalRequest(
                company.getId(), String.valueOf(기안자), "이수나", 기안(true, 팀장, 기안자, 원장));
        em.flush();
        em.clear();

        ApprovalRequestDTO submitted = service.submitDraft(draft.getId(), 기안자로그인, 기안(false, 팀장, 기안자, 원장));

        assertThat(결재자들(submitted)).containsExactly(팀장, 원장);
        assertThat(submitted.getApprovalLine().get(1).getRoleLabel()).isEqualTo("FINAL");
    }
}
