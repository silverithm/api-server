package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationBulkActionResponseDTO {
    
    private int totalRequested;
    private int successCount;
    private int failureCount;
    private List<Long> successIds;
    private List<Long> failureIds;
    private Map<Long, String> failureReasons;
    private String message;
}