package com.silverithm.vehicleplacementsystem.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class DispatchLocationsDTO {

    List<Location> elderlyLocations;
    List<Location> employeeLocations;

}
