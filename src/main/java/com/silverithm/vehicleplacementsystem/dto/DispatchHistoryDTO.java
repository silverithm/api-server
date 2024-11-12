package com.silverithm.vehicleplacementsystem.dto;

import java.time.LocalDateTime;

public record DispatchHistoryDTO(
        Long id,
        LocalDateTime createdAt,
        int totalEmployees,
        int totalElders
) {}