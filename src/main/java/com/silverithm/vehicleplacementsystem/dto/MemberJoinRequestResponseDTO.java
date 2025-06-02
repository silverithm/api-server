package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.MemberJoinRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberJoinRequestResponseDTO {
    
    private Long id;
    private String username;
    private String name;
    private String email;
    private String phoneNumber;
    private String requestedRole;
    private String department;
    private String position;
    private CompanyListDTO company;
    private String status;
    private String rejectReason;
    private Long approvedBy;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static MemberJoinRequestResponseDTO fromEntity(MemberJoinRequest entity) {
        return MemberJoinRequestResponseDTO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .name(entity.getName())
                .email(entity.getEmail())
                .phoneNumber(entity.getPhoneNumber())
                .requestedRole(entity.getRequestedRole().name().toLowerCase())
                .department(entity.getDepartment())
                .position(entity.getPosition())
                .company(entity.getCompany() != null ? CompanyListDTO.fromEntity(entity.getCompany()) : null)
                .status(entity.getStatus().name().toLowerCase())
                .rejectReason(entity.getRejectReason())
                .approvedBy(entity.getApprovedBy())
                .processedAt(entity.getProcessedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
} 