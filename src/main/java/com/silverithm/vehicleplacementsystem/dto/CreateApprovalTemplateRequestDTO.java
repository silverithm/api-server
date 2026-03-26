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

    private String templateType;

    private String formSchema;

    private String fileUrl;

    private String fileName;

    private Long fileSize;
}
