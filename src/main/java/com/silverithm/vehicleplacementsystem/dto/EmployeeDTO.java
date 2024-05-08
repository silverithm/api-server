package com.silverithm.vehicleplacementsystem.dto;

import lombok.Getter;

public record EmployeeDTO(Long id, String name, String homeAddressName, String workPlaceName, Location homeAddress,
                          Location workplace, int maximumCapacity, Boolean isDriver) {
}
