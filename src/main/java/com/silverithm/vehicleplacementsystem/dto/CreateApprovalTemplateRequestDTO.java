package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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

    /**
     * 이 양식으로 기안한 문서를 볼 수 있는 대상 (직책 또는 개인).
     *
     * null이면 기존 설정을 그대로 두고, 빈 배열이면 지정을 모두 지운다.
     */
    private List<ApprovalViewerEntryDTO> defaultViewers;
}
