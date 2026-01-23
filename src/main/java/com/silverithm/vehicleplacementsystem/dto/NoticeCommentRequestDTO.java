package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
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
public class NoticeCommentRequestDTO {

    @NotBlank(message = "내용을 입력해주세요")
    @Size(max = 1000, message = "댓글은 1000자를 초과할 수 없습니다")
    private String content;

    private String authorId;
    private String authorName;
}