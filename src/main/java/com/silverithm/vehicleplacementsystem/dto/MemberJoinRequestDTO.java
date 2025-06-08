package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberJoinRequestDTO {
    
    @NotBlank(message = "사용자명은 필수입니다")
    @Size(min = 4, max = 50, message = "사용자명은 4-50자여야 합니다")
    private String username;
    
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 6, max = 100, message = "비밀번호는 6자 이상이어야 합니다")
    private String password;
    
    @NotBlank(message = "이름은 필수입니다")
    private String name;
    
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email;
    
    private String phoneNumber;
    
    @NotBlank(message = "역할은 필수입니다")
    private String role;
    
    private String department;
    private String position;
    private String fcmToken;
    
    @NotNull(message = "회사 ID는 필수입니다")
    private Long companyId;
} 