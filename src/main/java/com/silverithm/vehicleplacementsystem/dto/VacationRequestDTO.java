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
    private String vacationType;
    private boolean substitute;
    private String duration;
    private String durationDisplayName;
    private double durationDays;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static VacationRequestDTO fromEntity(VacationRequest entity) {
        return fromEntity(entity, null);
    }

    /**
     * @param resolvedRole 회원에게 현재 배정된 역할. null/blank면 신청 당시 저장된 역할을 사용한다.
     */
    public static VacationRequestDTO fromEntity(VacationRequest entity, String resolvedRole) {
        return VacationRequestDTO.builder()
                .id(entity.getId())
                .userName(entity.getUserName())
                .date(entity.getDate())
                .status(entity.getStatus().name().toLowerCase())
                .role(resolvedRole != null && !resolvedRole.isBlank()
                        ? VacationRequest.normalizeRole(resolvedRole)
                        : entity.getNormalizedRole())
                .reason(entity.getReason())
                .userId(entity.getUserId())
                .type(entity.getType())
                .vacationType(entity.getVacationType())
                .substitute(entity.isSubstitute())
                .duration(entity.getDuration())
                .durationDisplayName(entity.getDurationEnum().getDisplayName())
                .durationDays(entity.getDurationEnum().getDays())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
} 
