package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomCreateRequest {

    @NotBlank(message = "채팅방 이름은 필수입니다")
    private String name;

    private String description;

    @NotBlank(message = "생성자 ID는 필수입니다")
    private String createdBy;

    @NotBlank(message = "생성자 이름은 필수입니다")
    private String createdByName;

    @NotEmpty(message = "참가자 목록은 필수입니다")
    private List<String> participantIds;
}
