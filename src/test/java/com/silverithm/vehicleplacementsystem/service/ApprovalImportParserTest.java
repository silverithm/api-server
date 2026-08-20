package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.dto.ApprovalImportRowDTO;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 이관 색인 파서.
 *
 * <p>내보내기 엑셀의 열 이름과 표기는 시스템·설정마다 달라서, 실제 파일을 받기 전에
 * 우리가 세운 가정(열 별칭, 날짜 형식, 결재자 표기)이 실제로 통하는지 여기서 고정해둔다.
 */
class ApprovalImportParserTest {

    private final ApprovalImportParser parser = new ApprovalImportParser();

    private MockMultipartFile excel(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("결재목록");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "index.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    @DisplayName("표준 열 이름을 알아보고 한 줄을 읽는다")
    void parsesStandardColumns() throws Exception {
        var file = excel(new String[][]{
                {"문서번호", "제목", "기안자", "기안일", "결재상태", "첨부파일"},
                {"2025-001", "1월 정기회의록", "홍길동", "2025-01-03", "완료", "회의록.pdf"},
        });

        var parsed = parser.parse(file);
        assertThat(parsed.unmappedColumns()).isEmpty();

        ApprovalImportRowDTO row = parsed.rows().get(0);
        assertThat(row.getExternalDocNumber()).isEqualTo("2025-001");
        assertThat(row.getTitle()).isEqualTo("1월 정기회의록");
        assertThat(row.getRequesterName()).isEqualTo("홍길동");
        assertThat(row.getDraftedAt()).isEqualTo(LocalDate.of(2025, 1, 3));
        assertThat(row.getStatus()).isEqualTo("APPROVED");
        assertThat(row.getFileNames()).containsExactly("회의록.pdf");
    }

    @Test
    @DisplayName("맨 위에 조회조건 줄이 붙어 있어도 헤더를 찾는다")
    void findsHeaderBelowTitleRows() throws Exception {
        var file = excel(new String[][]{
                {"○○재가복지센터"},
                {"조회기간: 2025-01-01 ~ 2025-12-31"},
                {},
                {"제목", "기안일", "결재상태"},
                {"지출품의", "2025/02/14", "결재완료"},
        });

        var parsed = parser.parse(file);
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().get(0).getDraftedAt()).isEqualTo(LocalDate.of(2025, 2, 14));
    }

    @Test
    @DisplayName("결재자를 한 칸에 나열한 표기를 순서대로 읽는다")
    void parsesApproversInSingleCell() throws Exception {
        var file = excel(new String[][]{
                {"제목", "기안일", "결재상태", "결재자", "결재일"},
                {"회의록", "2025-03-02", "완료", "김검토, 이과장 → 박원장", "2025-03-03, 2025-03-04, 2025-03-05"},
        });

        List<ApprovalImportRowDTO.Approver> approvers = parser.parse(file).rows().get(0).getApprovers();
        assertThat(approvers).extracting(ApprovalImportRowDTO.Approver::getName)
                .containsExactly("김검토", "이과장", "박원장");
        assertThat(approvers.get(2).getApprovedAt()).isEqualTo(LocalDate.of(2025, 3, 5));
    }

    @Test
    @DisplayName("이름 뒤 괄호에 날짜가 붙은 표기와 직책이 붙은 표기를 가른다")
    void parsesApproverWithParenthesis() throws Exception {
        var file = excel(new String[][]{
                {"제목", "기안일", "결재상태", "결재자"},
                {"회의록", "2025-03-02", "완료", "김검토(2025-03-03), 박원장(원장)"},
        });

        List<ApprovalImportRowDTO.Approver> approvers = parser.parse(file).rows().get(0).getApprovers();
        assertThat(approvers).extracting(ApprovalImportRowDTO.Approver::getName)
                .containsExactly("김검토", "박원장");
        assertThat(approvers.get(0).getApprovedAt()).isEqualTo(LocalDate.of(2025, 3, 3));
        assertThat(approvers.get(1).getApprovedAt()).isNull();
    }

    @Test
    @DisplayName("결재자1·결재일1 처럼 번호가 붙은 열도 짝지어 읽는다")
    void parsesNumberedApproverColumns() throws Exception {
        var file = excel(new String[][]{
                {"제목", "기안일", "결재상태", "결재자1", "결재일1", "결재자2", "결재일2"},
                {"회의록", "2025-03-02", "완료", "김검토", "2025-03-03", "박원장", "2025-03-04"},
        });

        List<ApprovalImportRowDTO.Approver> approvers = parser.parse(file).rows().get(0).getApprovers();
        assertThat(approvers).extracting(ApprovalImportRowDTO.Approver::getName)
                .containsExactly("김검토", "박원장");
        assertThat(approvers.get(1).getApprovedAt()).isEqualTo(LocalDate.of(2025, 3, 4));
    }

    @Test
    @DisplayName("반려와 진행중을 가려낸다 — 진행중은 이관 대상이 아니라 원문이 남는다")
    void normalizesStatus() throws Exception {
        var file = excel(new String[][]{
                {"제목", "기안일", "상태"},
                {"가", "2025-01-01", "반려"},
                {"나", "2025-01-02", "진행중"},
        });

        var rows = parser.parse(file).rows();
        assertThat(rows.get(0).getStatus()).isEqualTo("REJECTED");
        assertThat(rows.get(1).getStatus()).isEqualTo("진행중");
    }

    @Test
    @DisplayName("모르는 열은 버리지 않고 돌려준다")
    void reportsUnmappedColumns() throws Exception {
        var file = excel(new String[][]{
                {"제목", "기안일", "결재상태", "부서코드"},
                {"가", "2025-01-01", "완료", "A100"},
        });

        assertThat(parser.parse(file).unmappedColumns()).containsExactly("부서코드");
    }
}
