package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApprovalAttachmentRequestDTO {

    @NotBlank(message = "첨부파일 URL은 필수입니다")
    private String attachmentUrl;

    private String attachmentFileName;
    private Long attachmentFileSize;
}
