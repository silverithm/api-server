package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 확정 등록 요청 — 미리보기에서 확인한 줄들을 그대로 돌려보낸다 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalImportRequestDTO {

    /** 이관 문서를 담을 양식. 비우면 '이관 문서' 양식을 자동으로 만들어 쓴다 */
    private Long templateId;

    /** 가져온 곳 표시 (예: ECOUNT) */
    private String source;

    @NotNull(message = "등록할 문서가 없습니다")
    private List<ApprovalImportRowDTO> rows = new ArrayList<>();

    /** 원본 파일명 → 업로드된 저장 경로 */
    private Map<String, UploadedFile> files = new HashMap<>();

    /** 이관 문서를 볼 수 있는 직책·개인 (비우면 관리자만 본다) */
    private List<ApprovalViewerEntryDTO> viewers;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadedFile {
        private String filePath;
        private Long fileSize;
    }
}
