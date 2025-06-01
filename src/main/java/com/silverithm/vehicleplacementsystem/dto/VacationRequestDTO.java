package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRequestDTO {
    
    private Long id;
    private String userName;
    private LocalDate date;
    private String status;
    private String role;
    private String reason;
    private String userId;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static VacationRequestDTO fromEntity(VacationRequest entity) {
        return VacationRequestDTO.builder()
                .id(entity.getId())
                .userName(entity.getUserName())
                .date(entity.getDate())
                .status(entity.getStatus().name().toLowerCase())
                .role(entity.getRole().name().toLowerCase())
                .reason(entity.getReason())
                .userId(entity.getUserId())
                .type(entity.getType())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
} 