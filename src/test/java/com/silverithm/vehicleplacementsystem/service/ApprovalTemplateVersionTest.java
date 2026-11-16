package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ApprovalTemplateDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalTemplateVersionDTO;
import com.silverithm.vehicleplacementsystem.dto.CreateApprovalTemplateRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateRepository;
import com.silverithm.vehicleplacementsystem.repository.ApprovalTemplateVersionRepository;
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
 * 결재 양식 버전 기록(2026-11-16).
 *
 * <p>테스트 DB(H2)는 JSON 칸에 넣은 글자를 문자열로 한 번 더 싸서 돌려준다(운영 MySQL은 아니다) —
 * 그래서 양식 내용은 따옴표 없이 낱말로 확인한다.
 *
 * <p>숲속 업무일지에서 '행정 업무'를 복제한 뒤 원본을 지우자 지난 문서 본문이 화면에서 사라졌고,
 * 양식의 예전 모습이 어디에도 없어 로그와 문서 내용으로 거꾸로 맞춰 되살렸다. 그래서 지킨다:
 * <ul>
 *   <li>만들 때·고칠 때마다 그때 모습이 버전으로 쌓인다. 처음 고칠 때 기록이 없으면 고치기 전 모습부터 남는다.</li>
 *   <li>모양이 그대로인 저장(열람 대상만 바꿈, JSON 띄어쓰기만 다름)은 버전을 만들지 않는다.</li>
 *   <li>예전 버전으로 되돌리면 그 모습이 지금 양식이 되고, 되돌림도 새 버전으로 남는다(되돌리기 전 모습도 그대로).</li>
 *   <li>다른 양식의 버전으로는 되돌릴 수 없다.</li>
 * </ul>
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
        "spring.datasource.url=jdbc:h2:mem:tmplversiontest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=WARN"
})
class ApprovalTemplateVersionTest {

    @Autowired
    private ApprovalTemplateRepository templateRepository;
    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private ApprovalTemplateVersionRepository versionRepository;
    @Autowired
    private EntityManager em;

    private ApprovalTemplateService service;
    private Company company;

    private static final String 원래양식 = "{\"version\":1,\"fields\":["
            + "{\"id\":\"msod0n45pxcbv0rb40k\",\"type\":\"section\",\"label\":\"금일 실시 사항\"},"
            + "{\"id\":\"content\",\"type\":\"textarea\",\"label\":\"행정 업무\",\"required\":true},"
            + "{\"id\":\"issues\",\"type\":\"textarea\",\"label\":\"어르신 특이사항\"}]}";
    /** 복제한 뒤 원본을 지운 모습 — 지난 문서의 'content' 값이 그릴 자리를 잃었다 */
    private static final String 사본만남은양식 = "{\"version\":1,\"fields\":["
            + "{\"id\":\"msod0n45pxcbv0rb40k\",\"type\":\"section\",\"label\":\"금일 실시 사항\"},"
            + "{\"id\":\"mwimrby44qj5y9343e3\",\"type\":\"textarea\",\"label\":\"행정 업무 사본\",\"required\":true},"
            + "{\"id\":\"issues\",\"type\":\"textarea\",\"label\":\"어르신 특이사항\"}]}";

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("숲속재활어르신재가복지센터", null, null));

        ResourceScopeGuard guard = mock(ResourceScopeGuard.class);
        doNothing().when(guard).requireSameCompany(any(Company.class));

        WorkTaskAccess callerAccess = mock(WorkTaskAccess.class);
        when(callerAccess.requireCaller()).thenReturn(new WorkTaskAccess.Caller("admin_3", "김원장", company, true));

        ApprovalTemplateVersionService versionService =
                new ApprovalTemplateVersionService(versionRepository, callerAccess, new ObjectMapper());
        service = new ApprovalTemplateService(templateRepository, approvalRequestRepository, companyRepository,
                guard, mock(ApprovalViewerResolver.class), versionService);
    }

    private static CreateApprovalTemplateRequestDTO 요청(String schema) {
        CreateApprovalTemplateRequestDTO req = new CreateApprovalTemplateRequestDTO();
        req.setName("업무일지");
        req.setCategory("공문");
        req.setTemplateType("form");
        req.setFormSchema(schema);
        return req;
    }

    private void 실제로저장한다() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("만들 때·고칠 때마다 버전이 쌓이고, 최근 것이 지금 양식이다")
    void 저장마다버전() {
        ApprovalTemplateDTO created = service.createTemplate(company.getId(), 요청(원래양식));
        실제로저장한다();
        service.updateTemplate(created.getId(), 요청(사본만남은양식));
        실제로저장한다();

        List<ApprovalTemplateVersionDTO> versions = service.getVersions(created.getId());
        assertThat(versions).extracting(ApprovalTemplateVersionDTO::getVersionNo).containsExactly(2, 1);
        assertThat(versions).extracting(ApprovalTemplateVersionDTO::getKind).containsExactly("EDITED", "CREATED");
        assertThat(versions.get(0).isCurrent()).isTrue();
        assertThat(versions.get(1).isCurrent()).isFalse();
        assertThat(versions.get(0).getSavedByName()).isEqualTo("김원장");
        assertThat(versions.get(0).getSavedById()).isEqualTo("admin_3");
        assertThat(versions.get(1).getFormSchema()).contains("content").doesNotContain("사본");
    }

    @Test
    @DisplayName("기록이 없는 양식을 처음 고치면 고치기 전 모습이 먼저 남는다")
    void 기록없는양식은고치기전모습부터() {
        // 배포 전에 만들어져 버전이 없는 양식
        ApprovalTemplate old = templateRepository.save(ApprovalTemplate.builder()
                .company(company).name("업무일지").category("공문").templateType("form")
                .formSchema(원래양식).isActive(true).build());
        실제로저장한다();

        service.updateTemplate(old.getId(), 요청(사본만남은양식));
        실제로저장한다();

        List<ApprovalTemplateVersionDTO> versions = service.getVersions(old.getId());
        assertThat(versions).extracting(ApprovalTemplateVersionDTO::getKind).containsExactly("EDITED", "BASELINE");
        assertThat(versions.get(1).getFormSchema()).contains("행정 업무").doesNotContain("사본");
        assertThat(versions.get(1).getSavedByName()).isNull();
    }

    @Test
    @DisplayName("모양이 그대로인 저장은 버전을 만들지 않는다 — JSON 띄어쓰기·키 순서만 달라도")
    void 그대로저장은버전없음() {
        ApprovalTemplateDTO created = service.createTemplate(company.getId(), 요청(원래양식));
        실제로저장한다();
        String 띄어쓰기만다름 = 원래양식.replace(",\"fields\"", ", \"fields\"").replace("{\"version\":1,", "{ \"version\": 1,");
        service.updateTemplate(created.getId(), 요청(띄어쓰기만다름));
        실제로저장한다();

        assertThat(service.getVersions(created.getId())).hasSize(1);
    }

    @Test
    @DisplayName("예전 버전으로 되돌리면 그 모습이 지금 양식이 되고, 되돌림도 새 버전으로 남는다")
    void 되돌리기() {
        ApprovalTemplateDTO created = service.createTemplate(company.getId(), 요청(원래양식));
        실제로저장한다();
        service.updateTemplate(created.getId(), 요청(사본만남은양식));
        실제로저장한다();
        Long v1 = service.getVersions(created.getId()).get(1).getId();

        ApprovalTemplateDTO restored = service.restoreVersion(created.getId(), v1);
        실제로저장한다();

        assertThat(restored.getFormSchema()).contains("content").doesNotContain("사본");
        assertThat(templateRepository.findById(created.getId()).orElseThrow().getFormSchema())
                .contains("content").doesNotContain("사본");
        List<ApprovalTemplateVersionDTO> versions = service.getVersions(created.getId());
        assertThat(versions).extracting(ApprovalTemplateVersionDTO::getKind)
                .containsExactly("RESTORED", "EDITED", "CREATED");
        assertThat(versions.get(0).getRestoredFromNo()).isEqualTo(1);
        // 되돌리기 전 모습(사본만 남은 양식)도 그대로 있다 — 다시 돌아갈 수 있다
        assertThat(versions.get(1).getFormSchema()).contains("사본");
    }

    @Test
    @DisplayName("다른 양식의 버전으로는 되돌릴 수 없다")
    void 다른양식버전은안된다() {
        ApprovalTemplateDTO a = service.createTemplate(company.getId(), 요청(원래양식));
        ApprovalTemplateDTO b = service.createTemplate(company.getId(), 요청(사본만남은양식));
        실제로저장한다();
        Long bVersion = service.getVersions(b.getId()).get(0).getId();

        assertThatThrownBy(() -> service.restoreVersion(a.getId(), bVersion))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("버전을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("누가 저장했는지 몰라도 버전은 남는다")
    void 저장한사람몰라도남는다() {
        WorkTaskAccess unknown = mock(WorkTaskAccess.class);
        when(unknown.requireCaller()).thenThrow(new CustomException("로그인이 필요합니다", org.springframework.http.HttpStatus.UNAUTHORIZED));
        ResourceScopeGuard guard = mock(ResourceScopeGuard.class);
        ApprovalTemplateService anonymous = new ApprovalTemplateService(templateRepository, approvalRequestRepository,
                companyRepository, guard, mock(ApprovalViewerResolver.class),
                new ApprovalTemplateVersionService(versionRepository, unknown, new ObjectMapper()));

        ApprovalTemplateDTO created = anonymous.createTemplate(company.getId(), 요청(원래양식));
        실제로저장한다();

        List<ApprovalTemplateVersionDTO> versions = anonymous.getVersions(created.getId());
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getSavedByName()).isNull();
    }
}
