package com.silverithm.vehicleplacementsystem.dto;

import java.util.List;

public record AssignmentResponseDTO(String employeeName, int time, List<String> assignmentElderNames) {

}
