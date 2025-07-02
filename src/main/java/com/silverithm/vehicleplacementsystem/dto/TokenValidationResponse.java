package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationResponse {
    private boolean valid;
    private String message;
    private String userEmail;
    private String username;
    private Long userId;
    private Long expiresAt;

    public TokenValidationResponse(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public static TokenValidationResponse success(String userEmail, String username, Long userId, Long expiresAt) {
        return new TokenValidationResponse(true, "토큰이 유효합니다.", userEmail, username, userId, expiresAt);
    }

    public static TokenValidationResponse fail(String message) {
        return new TokenValidationResponse(false, message);
    }
} 