package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import java.util.List;
import java.util.UUID;
import lombok.Setter;

public record RequestDispatchDTO(List<ElderlyDTO> elderlys, List<CoupleRequestDTO> couples, List<EmployeeDTO> employees,
                                 CompanyDTO company, List<FixedAssignmentsDTO> fixedAssignments,
                                 DispatchType dispatchType, String userName) {
}
