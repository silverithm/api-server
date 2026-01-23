package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateNoticeRequestDTO {

    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다")
    private String title;

    @Size(max = 5000, message = "내용은 5000자를 초과할 수 없습니다")
    private String content;

    private String priority; // HIGH, NORMAL, LOW

    private Boolean isPinned;

    private String status; // DRAFT, PUBLISHED, ARCHIVED
}