package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 이관 색인 파일을 읽어본 결과 — 아직 저장하지 않은 상태 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalImportPreviewDTO {

    /** 인식한 열 이름 → 우리 항목 (화면에서 무엇이 무엇으로 읽혔는지 보여준다) */
    private List<ColumnMapping> columnMappings;

    /** 어느 항목으로도 인식하지 못한 열 이름 (무시된다) */
    private List<String> unmappedColumns;

    private List<ApprovalImportRowDTO> rows;

    private int totalCount;
    private int errorCount;

    /** 엑셀에 적혀 있으나 아직 올라오지 않은 파일 이름 */
    private List<String> missingFileNames;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColumnMapping {
        private String header;   // 엑셀의 열 이름
        private String field;    // 우리 항목 이름
    }
}
