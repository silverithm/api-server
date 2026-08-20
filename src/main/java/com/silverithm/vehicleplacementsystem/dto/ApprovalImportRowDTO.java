package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 이관 색인(엑셀) 한 줄.
 *
 * <p>미리보기 응답과 확정 등록 요청이 같은 모양을 쓴다 — 화면에서 고친 값이 그대로 다시 올라온다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalImportRowDTO {

    /** 엑셀에서의 행 번호 (1-based, 헤더 제외) — 오류를 어느 줄인지 짚어주기 위해 */
    private int rowNumber;

    private String externalDocNumber;
    private String title;
    private String requesterName;
    private LocalDate draftedAt;
    private String status;           // APPROVED | REJECTED
    private String category;         // 기안 종류 (선택)

    @Builder.Default
    private List<Approver> approvers = new ArrayList<>();

    /** 엑셀에 적힌 파일명들 — 업로드된 파일과 이름으로 맞춘다. 첫 번째가 대표 파일이 된다 */
    @Builder.Default
    private List<String> fileNames = new ArrayList<>();

    /** 이 줄을 등록할 수 없게 만드는 문제 (있으면 등록에서 제외된다) */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /** 등록은 되지만 알고 넘어가야 하는 것 (계정 못 찾음, 파일 없음 등) */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Approver {
        private String name;
        private LocalDate approvedAt;
        /** 이름으로 찾은 계정 (못 찾으면 null — 이름만 남는다) */
        private String matchedType;   // ADMIN | MEMBER
        private Long matchedRefId;
    }
}
