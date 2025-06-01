package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.VacationLimit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationLimitDTO {
    
    private Long id;
    private LocalDate date;
    private Integer maxPeople;
    private String role;
    
    public static VacationLimitDTO fromEntity(VacationLimit entity) {
        return VacationLimitDTO.builder()
                .id(entity.getId())
                .date(entity.getDate())
                .maxPeople(entity.getMaxPeople())
                .role(entity.getRole().name().toLowerCase())
                .build();
    }
} 