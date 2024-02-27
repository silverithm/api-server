package com.silverithm.vehicleplacementsystem.dto;

import lombok.Getter;

public record EmployeeDTO(Long id, Location homeAddress, Location workplace, int maximumCapacity) {
}
