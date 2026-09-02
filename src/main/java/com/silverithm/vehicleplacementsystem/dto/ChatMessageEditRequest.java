package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageEditRequest {

    @NotBlank(message = "내용은 필수입니다")
    @Size(max = 5000, message = "내용은 5000자를 넘을 수 없습니다")
    private String content;
}
