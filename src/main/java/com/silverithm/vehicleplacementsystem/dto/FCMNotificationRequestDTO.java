package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FCMNotificationRequestDTO {
    
    @NotBlank(message = "수신자 토큰은 필수입니다")
    private String recipientToken;
    
    @NotBlank(message = "제목은 필수입니다")
    private String title;
    
    @NotBlank(message = "메시지는 필수입니다")
    private String message;
    
    private String recipientUserId;
    private String recipientUserName;
    private String type;
    private Long relatedEntityId;
    private String relatedEntityType;
    private Map<String, String> data;
} 