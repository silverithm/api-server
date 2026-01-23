package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalTemplateDTO {

    private Long id;
    private String name;
    private String description;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ApprovalTemplateDTO from(ApprovalTemplate template) {
        return ApprovalTemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .fileUrl(template.getFileUrl())
                .fileName(template.getFileName())
                .fileSize(template.getFileSize())
                .isActive(template.getIsActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
