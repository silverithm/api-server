package com.silverithm.vehicleplacementsystem.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DispatchHistoryDetailDTO(
        Long id,
        LocalDateTime createdAt,
        List<AssignmentResponseDTO> assignments
) {}