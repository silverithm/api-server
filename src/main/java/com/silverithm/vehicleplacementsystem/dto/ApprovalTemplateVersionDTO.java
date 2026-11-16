package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplateVersion;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 양식 버전 기록 한 줄 — 미리보기·바뀐 점 비교를 위해 그때 양식 모습(formSchema)까지 준다 */
@Getter
@Builder
public class ApprovalTemplateVersionDTO {
    private Long id;
    private Integer versionNo;
    private String kind;
    private Integer restoredFromNo;
    private String name;
    private String description;
    private String category;
    private String templateType;
    private String formSchema;
    private String defaultApprovalLine;
    private String fileName;
    private String savedById;
    private String savedByName;
    private LocalDateTime createdAt;
    /** 지금 양식과 같은 버전(가장 최근) */
    private boolean current;

    public static ApprovalTemplateVersionDTO from(ApprovalTemplateVersion v, boolean current) {
        return ApprovalTemplateVersionDTO.builder()
                .id(v.getId())
                .versionNo(v.getVersionNo())
                .kind(v.getKind().name())
                .restoredFromNo(v.getRestoredFromNo())
                .name(v.getName())
                .description(v.getDescription())
                .category(v.getCategory())
                .templateType(v.getTemplateType())
                .formSchema(v.getFormSchema())
                .defaultApprovalLine(v.getDefaultApprovalLine())
                .fileName(v.getFileName())
                .savedById(v.getSavedById())
                .savedByName(v.getSavedByName())
                .createdAt(v.getCreatedAt())
                .current(current)
                .build();
    }
}
