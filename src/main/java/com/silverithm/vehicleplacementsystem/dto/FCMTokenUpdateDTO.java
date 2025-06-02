package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FCMTokenUpdateDTO {
    
    @NotBlank(message = "FCM 토큰은 필수입니다")
    private String fcmToken;
} 