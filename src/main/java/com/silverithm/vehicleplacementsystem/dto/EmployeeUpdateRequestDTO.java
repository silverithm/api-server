package com.silverithm.vehicleplacementsystem.dto;

public record EmployeeUpdateRequestDTO(String name, String homeAddress, String workPlace, int maxCapacity, Boolean isDriver) {
}
