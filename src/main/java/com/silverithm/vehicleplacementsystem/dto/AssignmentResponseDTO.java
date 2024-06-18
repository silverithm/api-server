package com.silverithm.vehicleplacementsystem.dto;

import java.util.List;

public record AssignmentResponseDTO(Long employeeId, Location homeAddress, Location workPlace, String employeeName,
                                    int time,
                                    List<AssignmentElderRequest> assignmentElders) {

}
