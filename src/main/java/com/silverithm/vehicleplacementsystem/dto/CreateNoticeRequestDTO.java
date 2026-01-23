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
public class CreateNoticeRequestDTO {

    @NotBlank(message = "제목을 입력해주세요")
    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다")
    private String title;

    @NotBlank(message = "내용을 입력해주세요")
    @Size(max = 5000, message = "내용은 5000자를 초과할 수 없습니다")
    private String content;

    @Builder.Default
    private String priority = "NORMAL"; // HIGH, NORMAL, LOW

    @Builder.Default
    private Boolean isPinned = false;

    @Builder.Default
    private Boolean sendPushNotification = true;
}