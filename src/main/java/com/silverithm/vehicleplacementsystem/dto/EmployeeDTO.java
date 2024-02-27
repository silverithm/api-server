package com.silverithm.vehicleplacementsystem.dto;

import lombok.Getter;

@Getter
public record EmployeeDTO(Long id, Location homeAddress, Location workplace) {
}
