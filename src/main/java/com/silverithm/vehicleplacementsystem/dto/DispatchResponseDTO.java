package com.silverithm.vehicleplacementsystem.dto;

import java.util.List;

public record DispatchResponseDTO(String jobId, Boolean success, List<AssignmentResponseDTO> result) {
}
