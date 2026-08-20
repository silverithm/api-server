package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ApprovalImportRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalImportRowDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.UserRole;
import com.silverithm.vehicleplacementsystem.repository.ApprovalRequestRepository;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.EntityManager;

/**
 * 기관이 양식을 내려받아 채운 뒤 대량으로 올리는 전 과정을 실제로 돌려본다.
 *
 * <p>양식 생성 → (기관이 채움) → 파서 → 검증 → DB 등록까지 한 줄로 이어서 확인한다.
 * 단위 테스트로는 각 조각만 보게 되어, 조각 사이에서 깨지는 것(파일명 공백, 중복 문서번호,
 * 수백 건일 때의 동작)을 놓친다.
 */
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class,
        ApprovalImportService.class, ApprovalImportParser.class, ApprovalImportTemplateWriter.class,
        ApprovalViewerResolver.class, ResourceScopeGuard.class, CallerCompanyResolver.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:importe2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=WARN"
})
class ApprovalImportEndToEndTest {

    @Autowired
    private ApprovalImportService importService;

    @Autowired
    private ApprovalImportParser parser;

    @Autowired
    private ApprovalImportTemplateWriter templateWriter;

    @Autowired
    private ApprovalRequestRepository requestRepository;

    @Autowired
    private EntityManager em;

    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.of("케어브이요양원", "서울시 강남구", new Location(37.5, 127.0));
        em.persist(company);

        AppUser admin = new AppUser("박원장", "owner@carev.test", "pw", UserRole.ROLE_CLIENT, "rt",
                company, "ck-1");
        em.persist(admin);

        em.persist(member("김검토", "reviewer"));
        em.persist(member("이영희", "younghee"));
        em.flush();

        // 서비스가 SecurityContext에서 요청자를 읽으므로 관리자로 로그인한 상태를 만든다
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@carev.test", "pw", List.of()));
    }

    private Member member(String name, String username) {
        Member member = Member.builder()
                .name(name)
                .username(username)
                .password("pw")
                .email(username + "@carev.test")
                .role(Member.Role.CAREGIVER)
                .status(Member.MemberStatus.ACTIVE)
                .company(company)
                .build();
        ReflectionTestUtils.setField(member, "createdAt", java.time.LocalDateTime.now());
        return member;
    }

    /** 기관이 내려받은 양식을 채우는 상황 — 헤더는 우리가 준 그대로 쓰고 데이터만 채운다 */
    private MockMultipartFile filledTemplate(int count) throws Exception {
        byte[] blank = templateWriter.write();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(blank));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheet("결재문서");

            // 예시 3줄을 지우고 (기관이 그러듯) 실제 데이터를 채운다
            for (int r = sheet.getLastRowNum(); r >= 1; r--) {
                if (sheet.getRow(r) != null) {
                    sheet.removeRow(sheet.getRow(r));
                }
            }

            for (int i = 0; i < count; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue("2025-" + String.format("%04d", i + 1));
                row.createCell(1).setCellValue((i + 1) + "월차 사례회의록");
                row.createCell(2).setCellValue(i % 2 == 0 ? "이영희" : "없는사람");
                row.createCell(3).setCellValue("2025-01-" + String.format("%02d", (i % 28) + 1));
                row.createCell(4).setCellValue(i % 10 == 0 ? "반려" : "완료");
                row.createCell(5).setCellValue("회의록");
                row.createCell(6).setCellValue("김검토");
                row.createCell(7).setCellValue("2025-02-01");
                row.createCell(8).setCellValue("박원장");
                row.createCell(9).setCellValue("2025-02-02");
                row.createCell(12).setCellValue("doc-" + (i + 1) + ".pdf");
            }

            workbook.write(out);
            return new MockMultipartFile("file", "채운양식.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    @DisplayName("양식을 채워 1000건을 올리면 그대로 등록된다")
    void bulkImportsFilledTemplate() throws Exception {
        int count = 1000;
        var file = filledTemplate(count);

        Map<String, ApprovalImportRequestDTO.UploadedFile> files = new HashMap<>();
        for (int i = 1; i <= count; i++) {
            files.put("doc-" + i + ".pdf",
                    new ApprovalImportRequestDTO.UploadedFile("approvals/doc-" + i + ".pdf", 1024L));
        }

        var preview = importService.preview(company.getId(), file, files.keySet());
        assertThat(preview.getTotalCount()).isEqualTo(count);
        assertThat(preview.getErrorCount()).isZero();
        assertThat(preview.getUnmappedColumns()).isEmpty();
        assertThat(preview.getMissingFileNames()).isEmpty();

        var request = new ApprovalImportRequestDTO();
        request.setSource("ECOUNT");
        request.setRows(preview.getRows());
        request.setFiles(files);

        var result = importService.importRows(company.getId(), request);
        assertThat(result.getErrorCount()).isZero();
        em.flush();
        em.clear();

        List<ApprovalRequest> saved = requestRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId());
        assertThat(saved).hasSize(count);

        ApprovalRequest first = saved.stream()
                .filter(r -> "2025-0001".equals(r.getExternalDocNumber()))
                .findFirst()
                .orElseThrow();
        assertThat(first.getIsImported()).isTrue();
        assertThat(first.getImportedSource()).isEqualTo("ECOUNT");
        assertThat(first.getStatus()).isEqualTo(ApprovalStatus.REJECTED);   // i=0 → 반려
        assertThat(first.getCreatedAt().toLocalDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(first.getAttachmentUrl()).isEqualTo("approvals/doc-1.pdf");
        assertThat(first.getSteps()).hasSize(2);
        assertThat(first.getSteps().get(0).getApproverName()).isEqualTo("김검토");
        // 계정이 있는 결재자는 연결되고, 없는 사람은 이름만 남는다
        assertThat(first.getSteps().get(0).getApproverRefId()).isNotNull();

        long unmatchedRequesters = saved.stream().filter(r -> "imported".equals(r.getRequesterId())).count();
        assertThat(unmatchedRequesters).isEqualTo(count / 2);   // '없는사람'은 계정에 못 붙는다
    }

    @Test
    @DisplayName("같은 파일을 두 번 올려도 중복으로 쌓이지 않는다")
    void rejectsAlreadyImportedDocNumbers() throws Exception {
        var file = filledTemplate(5);

        var first = importService.preview(company.getId(), file, Set.of());
        var request = new ApprovalImportRequestDTO();
        request.setRows(first.getRows());
        importService.importRows(company.getId(), request);
        em.flush();
        em.clear();

        var again = importService.preview(company.getId(), filledTemplate(5), Set.of());
        assertThat(again.getErrorCount()).isEqualTo(5);
        assertThat(again.getRows().get(0).getErrors().get(0)).contains("이미 옮겨진 문서번호");

        var secondRequest = new ApprovalImportRequestDTO();
        secondRequest.setRows(again.getRows());
        var result = importService.importRows(company.getId(), secondRequest);
        assertThat(result.getErrorCount()).isEqualTo(5);

        em.flush();
        em.clear();
        assertThat(requestRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId())).hasSize(5);
    }

    @Test
    @DisplayName("문제 있는 줄만 빠지고 나머지는 들어간다")
    void skipsOnlyBadRows() throws Exception {
        byte[] blank = templateWriter.write();
        MockMultipartFile file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(blank));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheet("결재문서");
            for (int r = sheet.getLastRowNum(); r >= 1; r--) {
                if (sheet.getRow(r) != null) {
                    sheet.removeRow(sheet.getRow(r));
                }
            }

            Row ok = sheet.createRow(1);
            ok.createCell(0).setCellValue("2025-100");
            ok.createCell(1).setCellValue("정상 문서");
            ok.createCell(3).setCellValue("2025-01-05");
            ok.createCell(4).setCellValue("완료");

            Row noTitle = sheet.createRow(2);
            noTitle.createCell(0).setCellValue("2025-101");
            noTitle.createCell(3).setCellValue("2025-01-06");
            noTitle.createCell(4).setCellValue("완료");

            Row inProgress = sheet.createRow(3);
            inProgress.createCell(0).setCellValue("2025-102");
            inProgress.createCell(1).setCellValue("진행중 문서");
            inProgress.createCell(3).setCellValue("2025-01-07");
            inProgress.createCell(4).setCellValue("진행중");

            Row noDate = sheet.createRow(4);
            noDate.createCell(0).setCellValue("2025-103");
            noDate.createCell(1).setCellValue("기안일 없음");
            noDate.createCell(4).setCellValue("완료");

            workbook.write(out);
            file = new MockMultipartFile("file", "일부오류.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }

        var preview = importService.preview(company.getId(), file, Set.of());
        assertThat(preview.getTotalCount()).isEqualTo(4);
        assertThat(preview.getErrorCount()).isEqualTo(3);

        var request = new ApprovalImportRequestDTO();
        request.setRows(preview.getRows());
        var result = importService.importRows(company.getId(), request);

        em.flush();
        em.clear();
        var saved = requestRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTitle()).isEqualTo("정상 문서");
        assertThat(result.getErrorCount()).isEqualTo(3);
    }
}
