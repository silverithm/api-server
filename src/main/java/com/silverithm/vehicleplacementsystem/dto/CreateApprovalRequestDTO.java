package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateApprovalRequestDTO {

    @NotNull(message = "템플릿 ID는 필수입니다")
    private Long templateId;

    @NotBlank(message = "제목은 필수입니다")
    private String title;

    // 첨부파일 정보 (선택)
    private String attachmentUrl;
    private String attachmentFileName;
    private Long attachmentFileSize;
}
