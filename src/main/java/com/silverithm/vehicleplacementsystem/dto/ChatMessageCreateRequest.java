package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageCreateRequest {

    @NotBlank(message = "발신자 ID는 필수입니다")
    private String senderId;

    @NotBlank(message = "발신자 이름은 필수입니다")
    private String senderName;

    private String type; // TEXT, IMAGE, FILE, SYSTEM

    private String content;

    // 파일 메시지용
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
}
