package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import java.util.List;

public record RequestDispatchDTO(List<ElderlyDTO> elderlys, List<EmployeeDTO> employees,
                                 CompanyDTO company, List<FixedAssignmentsDTO> fixedAssignments,
                                 DispatchType dispatchType) {
}
