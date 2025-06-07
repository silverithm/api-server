package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Member;
import java.time.LocalDateTime;

public record MemberSigninResponseDTO(
        Long memberId, 
        String username, 
        String name, 
        String email,
        String role,
        String status,
        String department,
        String position,
        CompanyListDTO company,
        LocalDateTime lastLoginAt,
        UserResponseDTO.TokenInfo tokenInfo
) {
    
    public static MemberSigninResponseDTO from(Member member, UserResponseDTO.TokenInfo tokenInfo) {
        return new MemberSigninResponseDTO(
                member.getId(),
                member.getUsername(),
                member.getName(),
                member.getEmail(),
                member.getRole().name().toLowerCase(),
                member.getStatus().name().toLowerCase(),
                member.getDepartment(),
                member.getPosition(),
                member.getCompany() != null ? CompanyListDTO.fromEntity(member.getCompany()) : null,
                member.getLastLoginAt(),
                tokenInfo
        );
    }
} 