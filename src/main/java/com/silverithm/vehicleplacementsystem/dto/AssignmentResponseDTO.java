package com.silverithm.vehicleplacementsystem.dto;

import java.util.List;

public record AssignmentResponseDTO(Long employeeId, String employeeName, int time,
                                    List<AssignmentElderRequest> assignmentElders) {

}
