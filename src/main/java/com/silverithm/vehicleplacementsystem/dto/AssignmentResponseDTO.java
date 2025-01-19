package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import java.util.List;

public record AssignmentResponseDTO(DispatchType dispatchType, Long employeeId, Location homeAddress,
                                    Location workPlace, String employeeName,
                                    int time,
                                    List<AssignmentElderRequest> assignmentElders, Boolean isDriver) {

}
