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
    /**
     * 알림을 일으킨 사람(채팅 식별자 "12" / "admin_3"). null이면 이 요청을 보낸 로그인 사용자로 채운다
     * (리마인더처럼 로그인 사용자가 없으면 비운다). 빈 문자열이면 일부러 비운다.
     */
    private String actorId;
    private Map<String, String> data;
} 