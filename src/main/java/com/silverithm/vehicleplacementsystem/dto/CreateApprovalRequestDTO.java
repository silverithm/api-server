package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateApprovalRequestDTO {

    @NotNull(message = "템플릿 ID는 필수입니다")
    private Long templateId;

    @NotBlank(message = "제목은 필수입니다")
    private String title;

    // 폼 데이터 (form 타입 양식)
    private String formData;

    // 첨부파일 정보 (선택)
    private String attachmentUrl;
    private String attachmentFileName;
    private Long attachmentFileSize;

    // 결재선 (선택 — 없으면 기존 단일 승인 방식, 앱은 이 필드를 보내지 않음)
    private List<ApprovalLineEntryDTO> approvalLine;

    /**
     * 반려된 기안을 고쳐 올리는 경우 그 원본 id.
     * 주면 새 기안이 그 문서의 다음 차수로 이어진다 (원본은 반려 상태 그대로 남는다).
     */
    private Long revisedFromId;

    /**
     * 열람 대상 (선택).
     *
     * 보내지 않으면(null) 양식에 지정된 기본 열람 대상이 그대로 적용된다.
     * 빈 배열을 보내면 기본값 없이 "지정 없음"이 된다 — 앱처럼 이 필드를 모르는 클라이언트가
     * 기본값을 지우지 않도록 null과 빈 배열을 구분한다.
     */
    private List<ApprovalViewerEntryDTO> viewers;

    /**
     * 상신하지 않고 임시저장만 한다.
     *
     * 임시저장은 결재함에 뜨지 않고 알림도 나가지 않으며, 결재선을 아직 안 정했어도 저장된다.
     * 값을 보내지 않으면 예전처럼 곧바로 상신된다.
     */
    private Boolean draft;

    public boolean isDraft() {
        return Boolean.TRUE.equals(draft);
    }
}
