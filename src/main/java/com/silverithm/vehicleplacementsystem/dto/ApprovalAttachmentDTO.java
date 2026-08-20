package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 결재 문서에 딸린 추가 첨부파일 한 개 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalAttachmentDTO {

    private String fileUrl;
    private String fileName;
    private Long fileSize;
}
