package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Elderly;
import java.util.List;
import lombok.Getter;

@Getter
public class DispatchLocationsDTO {

    List<ElderlyDTO> elderlyLocations;
    List<EmployeeDTO> employeeLocations;
    CompanyDTO company;

}
