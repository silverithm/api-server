package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationDateResponseDTO {
    
    private String date;
    private List<VacationRequestDTO> vacations;
    private Integer totalVacationers;
    private Integer maxPeople;
} 