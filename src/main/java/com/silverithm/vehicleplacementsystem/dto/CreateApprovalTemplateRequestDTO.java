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
public class CreateApprovalTemplateRequestDTO {

    @NotBlank(message = "양식명은 필수입니다")
    private String name;

    private String description;

    @NotBlank(message = "파일 URL은 필수입니다")
    private String fileUrl;

    @NotBlank(message = "파일명은 필수입니다")
    private String fileName;

    @NotNull(message = "파일 크기는 필수입니다")
    private Long fileSize;
}
