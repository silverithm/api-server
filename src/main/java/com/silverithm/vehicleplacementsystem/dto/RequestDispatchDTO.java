package com.silverithm.vehicleplacementsystem.dto;

import java.util.List;
import lombok.Getter;

public record RequestDispatchDTO(List<ElderlyDTO> elderlys, List<EmployeeDTO> employees,
                                 CompanyDTO company) {
}
