package com.silverithm.vehicleplacementsystem.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 기관에 나눠줄 이관 색인 양식(엑셀)을 만든다.
 *
 * <p>쓰던 시스템의 내보내기 파일을 그대로 올려도 파서가 열 이름을 알아보긴 하지만,
 * 시스템마다 열 구성이 달라 한 번은 사람이 확인해야 한다. 표준 양식을 주면 기관이 채워 오기만
 * 하면 되고, 우리는 무엇이 올지 미리 안다.
 *
 * <p>여기의 열 이름은 {@link ApprovalImportParser}의 별칭 사전이 인식하는 이름이어야 한다 —
 * 우리가 준 양식을 우리가 못 읽으면 안 되므로 테스트로 묶어뒀다.
 */
@Component
public class ApprovalImportTemplateWriter {

    public static final String FILE_NAME = "케어브이_결재문서_이관양식.xlsx";

    /**
     * 채워 넣는 열 — 순서와 이름 모두 파서가 아는 것으로 맞춘다.
     * 필수 열(제목·기안일·결재상태)에는 *를 붙여 시트에서 바로 보이게 한다 (파서는 *를 무시한다).
     */
    private static final String[] HEADERS = {
            "문서번호", "제목*", "기안자", "기안일*", "결재상태*", "기안종류",
            "결재자1", "결재일1", "결재자2", "결재일2", "결재자3", "결재일3",
            "첨부파일",
    };

    /**
     * 예시 줄. 문서번호나 제목이 "(예시)"로 시작하면 파서가 건너뛰므로,
     * 기관이 지우지 않고 아래에 이어서 채워도 예시가 진짜 문서로 등록되지 않는다.
     */
    private static final String[][] SAMPLES = {
            {"(예시) 2025-001", "(예시) 1월 정기 사례회의록", "홍길동", "2025-01-03", "완료", "회의록",
                    "김검토", "2025-01-04", "박원장", "2025-01-05", "", "",
                    "2025-001_회의록.pdf"},
            {"(예시) 2025-002", "(예시) 1월 소모품 구입 지출품의", "이영희", "2025-01-08", "완료", "지출",
                    "박원장", "2025-01-09", "", "", "", "",
                    "2025-002_지출품의.pdf, 견적서.pdf"},
            {"(예시) 2025-003", "(예시) 2월 야유회 계획(반려)", "홍길동", "2025-02-11", "반려", "일반",
                    "박원장", "2025-02-12", "", "", "", "",
                    ""},
    };

    private static final String[][] GUIDE = {
            {"열", "필수", "설명"},
            {"", "", "제목·기안일·결재상태(*표시) 세 가지만 있으면 등록됩니다. 나머지는 아는 만큼만 채우세요."},
            {"", "", "회색 예시 줄은 지워도 되고 그대로 둬도 됩니다 — 문서번호나 제목이 (예시)로 시작하는 줄은 등록되지 않습니다."},
            {"문서번호", "권장", "쓰던 시스템의 문서번호를 그대로 적습니다. 같은 번호를 두 번 올리면 중복으로 걸러집니다."},
            {"제목", "필수", "비어 있으면 그 줄은 등록되지 않습니다."},
            {"기안자", "권장", "케어브이에 있는 직원 이름과 같으면 그 계정에 연결됩니다. 퇴사자는 이름만 남습니다."},
            {"기안일", "필수", "2025-01-03 형식을 권장합니다. 2025/01/03, 2025.01.03도 읽습니다."},
            {"결재상태", "필수", "완료 또는 반려만 넣습니다. 진행 중이던 문서는 옮길 수 없습니다."},
            {"기안종류", "선택", "회의록·지출·인사처럼 문서를 묶는 이름입니다. 결재함에서 이 분류로 골라 볼 수 있습니다."},
            {"결재자1~3", "선택", "결재한 순서대로 적습니다. 마지막 사람이 최종 결재자가 됩니다."},
            {"결재일1~3", "선택", "같은 번호의 결재자가 처리한 날짜입니다."},
            {"첨부파일", "선택", "파일 이름을 그대로 적습니다. 여러 개면 쉼표로 나눕니다. 업로드하는 파일과 이름이 같아야 붙습니다."},
            {"", "", ""},
            {"※ 결재자가 4명 이상이면", "", "결재자4·결재일4 처럼 열을 이어서 만들면 그대로 읽습니다."},
            {"※ 이 양식이 아니어도", "", "쓰던 시스템에서 내보낸 파일을 그대로 올려도 됩니다. 열 이름을 알아보지 못하면 화면에서 알려드립니다."},
            {"※ 옮겨온 문서는", "", "보관·열람용 기록입니다. 결재함에서 검색·열람만 되고 승인·반려는 되지 않습니다."},
    };

    public byte[] write() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDataSheet(workbook);
            writeGuideSheet(workbook);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeDataSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("결재문서");
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle sampleStyle = sampleStyle(workbook);

        Row header = sheet.createRow(0);
        for (int c = 0; c < HEADERS.length; c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(HEADERS[c]);
            cell.setCellStyle(headerStyle);
        }

        // 예시는 회색 기울임으로 넣어 "지우고 채우는 줄"임을 알게 한다
        for (int r = 0; r < SAMPLES.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < SAMPLES[r].length; c++) {
                Cell cell = row.createCell(c);
                cell.setCellValue(SAMPLES[r][c]);
                cell.setCellStyle(sampleStyle);
            }
        }

        for (int c = 0; c < HEADERS.length; c++) {
            sheet.setColumnWidth(c, columnWidth(HEADERS[c]));
        }
        sheet.createFreezePane(0, 1);
    }

    private void writeGuideSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("작성요령");
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);

        for (int r = 0; r < GUIDE.length; r++) {
            Row row = sheet.createRow(r);
            for (int c = 0; c < GUIDE[r].length; c++) {
                Cell cell = row.createCell(c);
                cell.setCellValue(GUIDE[r][c]);
                cell.setCellStyle(r == 0 ? headerStyle : wrapStyle);
            }
        }

        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 8 * 256);
        sheet.setColumnWidth(2, 80 * 256);
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle sampleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        return style;
    }

    private int columnWidth(String header) {
        return switch (header) {
            case "제목" -> 34 * 256;
            case "첨부파일" -> 30 * 256;
            case "문서번호" -> 14 * 256;
            default -> 12 * 256;
        };
    }
}
