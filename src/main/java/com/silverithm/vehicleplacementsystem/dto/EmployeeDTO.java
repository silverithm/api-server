package com.silverithm.vehicleplacementsystem.dto;

import lombok.Getter;

public record EmployeeDTO(Long id, String name, Location homeAddress, Location workplace, int maximumCapacity) {
}
