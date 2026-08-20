package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ApprovalImportPreviewDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalImportRowDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 다른 시스템에서 내보낸 결재 색인(엑셀)을 읽는다.
 *
 * <p>내보내기 열 이름은 시스템마다, 같은 시스템이라도 설정마다 다르다. 그래서 열 위치를 고정하지 않고
 * 열 이름을 별칭 사전과 대조해 알아본다. 못 알아본 열은 버리지 않고 화면에 돌려줘서, 사람이
 * "이 열이 빠졌다"를 바로 볼 수 있게 한다.
 */
@Component
@Slf4j
public class ApprovalImportParser {

    /** 우리 항목 → 그 항목으로 인정할 열 이름들 (공백·괄호를 지운 소문자로 비교) */
    private static final Map<String, List<String>> COLUMN_ALIASES = new LinkedHashMap<>();

    static {
        COLUMN_ALIASES.put("externalDocNumber",
                List.of("문서번호", "문서no", "기안번호", "결재번호", "관리번호", "no", "번호", "docno"));
        COLUMN_ALIASES.put("title",
                List.of("제목", "기안제목", "문서제목", "건명", "title", "subject"));
        COLUMN_ALIASES.put("requesterName",
                List.of("기안자", "작성자", "신청자", "起案者", "requester", "writer", "drafter"));
        COLUMN_ALIASES.put("draftedAt",
                List.of("기안일", "기안일자", "작성일", "작성일자", "신청일", "등록일", "date", "draftdate"));
        COLUMN_ALIASES.put("status",
                List.of("결재상태", "상태", "진행상태", "처리상태", "status"));
        COLUMN_ALIASES.put("category",
                List.of("문서종류", "기안종류", "양식", "양식명", "분류", "대분류", "category", "formname"));
        COLUMN_ALIASES.put("approvers",
                List.of("결재자", "결재자명", "승인자", "결재선", "approver", "approvers"));
        COLUMN_ALIASES.put("approvedAt",
                List.of("결재일", "결재일자", "승인일", "완료일", "최종결재일", "approvaldate", "approveddate"));
        COLUMN_ALIASES.put("fileNames",
                List.of("첨부파일", "첨부파일명", "파일명", "첨부", "file", "filename", "attachment"));
    }

    /** 결재자 여러 명을 한 칸에 적을 때 쓰이는 구분자들 */
    private static final Pattern APPROVER_SPLIT = Pattern.compile("[,;/\n]|->|→");

    /** "홍길동(2025-01-03)" 처럼 이름 뒤 괄호에 날짜를 붙여 쓰는 표기 */
    private static final Pattern NAME_WITH_DATE = Pattern.compile("^(.*?)[(\\[]([^)\\]]*)[)\\]]\\s*$");

    /** 번호가 붙은 열 (결재자1, 결재일2 …) */
    private static final Pattern NUMBERED = Pattern.compile("^(.*?)(\\d+)$");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN));

    public record ParsedSheet(List<ApprovalImportPreviewDTO.ColumnMapping> mappings,
                              List<String> unmappedColumns,
                              List<ApprovalImportRowDTO> rows) {
    }

    public ParsedSheet parse(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("엑셀에 시트가 없습니다.");
            }

            int headerRowIndex = findHeaderRow(sheet);
            if (headerRowIndex < 0) {
                throw new IllegalArgumentException(
                        "열 이름을 찾지 못했습니다. 첫 줄에 '제목', '기안일' 같은 열 이름이 있는지 확인해주세요.");
            }

            Row headerRow = sheet.getRow(headerRowIndex);
            Map<Integer, String> columnFields = new LinkedHashMap<>();
            Map<Integer, Integer> columnSequence = new LinkedHashMap<>();  // 결재자1 → 1
            List<ApprovalImportPreviewDTO.ColumnMapping> mappings = new ArrayList<>();
            List<String> unmapped = new ArrayList<>();

            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String header = readString(headerRow.getCell(c));
                if (header == null || header.isBlank()) {
                    continue;
                }

                Matcher numbered = NUMBERED.matcher(normalize(header));
                String base = numbered.matches() ? numbered.group(1) : normalize(header);
                Integer sequence = numbered.matches() ? Integer.valueOf(numbered.group(2)) : null;

                String field = resolveField(base);
                if (field == null) {
                    unmapped.add(header.trim());
                    continue;
                }

                columnFields.put(c, field);
                if (sequence != null) {
                    columnSequence.put(c, sequence);
                }
                mappings.add(ApprovalImportPreviewDTO.ColumnMapping.builder()
                        .header(header.trim())
                        .field(field)
                        .build());
            }

            List<ApprovalImportRowDTO> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                ApprovalImportRowDTO parsed = readRow(row, r - headerRowIndex, columnFields, columnSequence);
                // 우리가 나눠준 양식의 예시 줄 — 지우지 않고 그대로 올려도 등록되지 않게 건너뛴다.
                // "(예시)"를 명시적으로 붙인 줄만 거르므로 진짜 문서가 잘못 걸러질 일은 없다.
                if (isSampleRow(parsed)) {
                    continue;
                }
                rows.add(parsed);
            }

            return new ParsedSheet(mappings, unmapped, rows);
        }
    }

    /**
     * 헤더 줄 찾기. 내보낸 엑셀은 맨 위에 회사명·조회조건 같은 줄이 붙어 오는 경우가 많아
     * 첫 줄이 헤더라고 단정하지 않고, 아는 열 이름이 둘 이상 보이는 첫 줄을 헤더로 본다.
     */
    private int findHeaderRow(Sheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), 20);
        for (int r = sheet.getFirstRowNum(); r <= limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int hits = 0;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String header = readString(row.getCell(c));
                if (header == null || header.isBlank()) {
                    continue;
                }
                Matcher numbered = NUMBERED.matcher(normalize(header));
                String base = numbered.matches() ? numbered.group(1) : normalize(header);
                if (resolveField(base) != null) {
                    hits++;
                }
            }
            if (hits >= 2) {
                return r;
            }
        }
        return -1;
    }

    private ApprovalImportRowDTO readRow(Row row, int rowNumber, Map<Integer, String> columnFields,
                                         Map<Integer, Integer> columnSequence) {
        ApprovalImportRowDTO dto = ApprovalImportRowDTO.builder().rowNumber(rowNumber).build();

        // 번호가 붙은 결재자/결재일 열을 순번끼리 짝지어 모은다 (결재자1 ↔ 결재일1)
        Map<Integer, String> approverNameBySeq = new LinkedHashMap<>();
        Map<Integer, LocalDate> approvedAtBySeq = new LinkedHashMap<>();
        String approverCell = null;
        String approvedAtCell = null;

        for (Map.Entry<Integer, String> entry : columnFields.entrySet()) {
            int column = entry.getKey();
            String field = entry.getValue();
            Cell cell = row.getCell(column);
            Integer sequence = columnSequence.get(column);

            switch (field) {
                case "externalDocNumber" -> dto.setExternalDocNumber(readString(cell));
                case "title" -> dto.setTitle(readString(cell));
                case "requesterName" -> dto.setRequesterName(readString(cell));
                case "draftedAt" -> dto.setDraftedAt(readDate(cell));
                case "status" -> dto.setStatus(normalizeStatus(readString(cell)));
                case "category" -> dto.setCategory(readString(cell));
                case "fileNames" -> dto.getFileNames().addAll(splitFileNames(readString(cell)));
                case "approvers" -> {
                    if (sequence != null) {
                        approverNameBySeq.put(sequence, readString(cell));
                    } else {
                        approverCell = readString(cell);
                    }
                }
                case "approvedAt" -> {
                    if (sequence != null) {
                        approvedAtBySeq.put(sequence, readDate(cell));
                    } else {
                        approvedAtCell = readString(cell);
                    }
                }
                default -> { /* 알 수 없는 항목은 무시 */ }
            }
        }

        dto.getApprovers().addAll(buildApprovers(approverCell, approvedAtCell, approverNameBySeq, approvedAtBySeq));
        return dto;
    }

    /**
     * 결재자 목록 만들기. 두 가지 표기를 모두 받는다.
     * <ul>
     *   <li>번호 열: 결재자1·결재일1, 결재자2·결재일2 …</li>
     *   <li>한 칸 나열: "홍길동, 김철수" + 결재일 "2025-01-03, 2025-01-04"
     *       또는 "홍길동(2025-01-03), 김철수(2025-01-04)"</li>
     * </ul>
     */
    private List<ApprovalImportRowDTO.Approver> buildApprovers(
            String approverCell, String approvedAtCell,
            Map<Integer, String> nameBySeq, Map<Integer, LocalDate> dateBySeq) {

        List<ApprovalImportRowDTO.Approver> approvers = new ArrayList<>();

        if (!nameBySeq.isEmpty()) {
            nameBySeq.entrySet().stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> approvers.add(ApprovalImportRowDTO.Approver.builder()
                            .name(e.getValue().trim())
                            .approvedAt(dateBySeq.get(e.getKey()))
                            .build()));
            return approvers;
        }

        if (approverCell == null || approverCell.isBlank()) {
            return approvers;
        }

        List<String> names = new ArrayList<>();
        for (String piece : APPROVER_SPLIT.split(approverCell)) {
            if (!piece.isBlank()) {
                names.add(piece.trim());
            }
        }

        List<LocalDate> dates = new ArrayList<>();
        if (approvedAtCell != null && !approvedAtCell.isBlank()) {
            for (String piece : APPROVER_SPLIT.split(approvedAtCell)) {
                if (!piece.isBlank()) {
                    dates.add(parseDate(piece.trim()));
                }
            }
        }

        for (int i = 0; i < names.size(); i++) {
            String raw = names.get(i);
            LocalDate date = i < dates.size() ? dates.get(i) : null;

            // "홍길동(2025-01-03)" 처럼 괄호 안에 날짜나 직책이 붙어 오는 표기
            Matcher matcher = NAME_WITH_DATE.matcher(raw);
            if (matcher.matches()) {
                String inside = matcher.group(2).trim();
                LocalDate inlineDate = parseDate(inside);
                if (inlineDate != null) {
                    date = inlineDate;
                }
                raw = matcher.group(1).trim();   // 날짜가 아니면 직책으로 보고 이름만 남긴다
            }

            if (!raw.isBlank()) {
                approvers.add(ApprovalImportRowDTO.Approver.builder().name(raw).approvedAt(date).build());
            }
        }

        // 결재일이 문서 단위로 하나만 있으면 마지막 결재자(최종 결재)에게 붙인다
        if (dates.size() == 1 && approvers.size() > 1) {
            approvers.forEach(a -> a.setApprovedAt(null));
            approvers.get(approvers.size() - 1).setApprovedAt(dates.get(0));
        }

        return approvers;
    }

    private List<String> splitFileNames(String value) {
        List<String> names = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return names;
        }
        for (String piece : value.split("[,;|\n]")) {
            if (!piece.isBlank()) {
                names.add(piece.trim());
            }
        }
        return names;
    }

    /** 시스템마다 다른 상태 표기를 승인/반려로 모은다. 알 수 없으면 원문을 그대로 돌려준다 */
    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = normalize(value);
        if (v.contains("반려") || v.contains("거부") || v.contains("reject")) {
            return "REJECTED";
        }
        if (v.contains("완료") || v.contains("승인") || v.contains("결재완료") || v.contains("approve")
                || v.contains("complete") || v.contains("확정")) {
            return "APPROVED";
        }
        return value.trim();
    }

    private String resolveField(String normalizedHeader) {
        for (Map.Entry<String, List<String>> entry : COLUMN_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (normalizedHeader.equals(normalize(alias))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /** 공백·괄호·밑줄·별표를 지우고 소문자로 — "문서 번호", "제목*"을 같은 것으로 본다 */
    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s()\\[\\]_·*]", "").toLowerCase(Locale.ROOT);
    }

    private String readString(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    // 문서번호가 숫자로 들어오면 1234.0이 되지 않게 정수로 떨군다
                    : stripTrailingZero(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> readFormula(cell);
            default -> null;
        };
    }

    private String readFormula(Cell cell) {
        try {
            return cell.getStringCellValue().trim();
        } catch (IllegalStateException e) {
            return stripTrailingZero(cell.getNumericCellValue());
        }
    }

    private String stripTrailingZero(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private LocalDate readDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        return parseDate(readString(cell));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 시각이 붙어 오면(2025-01-03 14:20) 날짜 부분만 본다
        String trimmed = value.trim().split("[ T]")[0];
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (Exception ignored) {
                // 다음 형식으로
            }
        }
        return null;
    }

    /** 문서번호나 제목이 "(예시)"로 시작하는 줄 — 배포 양식의 예시 데이터 */
    private boolean isSampleRow(ApprovalImportRowDTO row) {
        return startsWithSampleMarker(row.getExternalDocNumber()) || startsWithSampleMarker(row.getTitle());
    }

    private boolean startsWithSampleMarker(String value) {
        return value != null && value.trim().startsWith("(예시)");
    }

    private boolean isBlankRow(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String value = readString(row.getCell(c));
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
