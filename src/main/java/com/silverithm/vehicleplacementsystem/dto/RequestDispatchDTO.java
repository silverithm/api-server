package com.silverithm.vehicleplacementsystem.dto;

import java.util.List;
import lombok.Getter;

@Getter
public record RequestDispatchDTO(List<ElderlyDTO> elderlyLocations, List<EmployeeDTO> employeeLocations,
                                 CompanyDTO company) {
}
