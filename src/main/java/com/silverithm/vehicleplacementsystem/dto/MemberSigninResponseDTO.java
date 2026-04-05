package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Member;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
        List<String> permissions,
        LocalDateTime lastLoginAt,
        UserResponseDTO.TokenInfo tokenInfo
) {

    public static MemberSigninResponseDTO from(Member member, UserResponseDTO.TokenInfo tokenInfo) {
        Set<String> perms = member.getPermissions();
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
                perms != null ? List.copyOf(perms) : List.of(),
                member.getLastLoginAt(),
                tokenInfo
        );
    }
} 