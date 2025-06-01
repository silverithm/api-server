package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VacationLimitRequestDTO {
    
    private List<VacationLimitCreateDTO> limits;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VacationLimitCreateDTO {
        private String id;
        private String date;
        private Integer maxPeople;
        private String role;
    }
} 