package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.DispatchType;

public record DispatchHistoryDTO(
        Long id,
        String createdAt,
        int totalEmployees,
        int totalElders,
        int totalTime,
        DispatchType dispatchType
) {
}