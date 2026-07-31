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
}
