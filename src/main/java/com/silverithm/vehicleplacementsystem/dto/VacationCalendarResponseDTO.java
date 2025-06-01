package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationCalendarResponseDTO {
    
    private Map<String, VacationDateInfo> dates;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VacationDateInfo {
        private String date;
        private List<VacationRequestDTO> vacations;
        private Integer totalVacationers;
        private Integer maxPeople;
    }
} 