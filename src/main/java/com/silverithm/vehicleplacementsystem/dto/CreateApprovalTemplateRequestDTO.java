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
public class CreateApprovalTemplateRequestDTO {

    @NotBlank(message = "양식명은 필수입니다")
    private String name;

    private String description;

    /** 기안 대분류 (공문/교육/인사 등) */
    private String category;

    private String templateType;

    private String formSchema;
    private String defaultApprovalLine;

    private String fileUrl;

    private String fileName;

    private Long fileSize;
}
