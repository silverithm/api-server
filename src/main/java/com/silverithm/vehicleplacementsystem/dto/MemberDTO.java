package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberDTO {
    
    private Long id;
    private String username;
    private String name;
    private String email;
    private String phoneNumber;
    private String role;
    private String status;
    private String department;
    private String position;
    private CompanyListDTO company;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static MemberDTO fromEntity(Member entity) {
        return MemberDTO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .name(entity.getName())
                .email(entity.getEmail())
                .phoneNumber(entity.getPhoneNumber())
                .role(entity.getRole().name().toLowerCase())
                .status(entity.getStatus().name().toLowerCase())
                .department(entity.getDepartment())
                .position(entity.getPosition())
                .company(entity.getCompany() != null ? CompanyListDTO.fromEntity(entity.getCompany()) : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
} 