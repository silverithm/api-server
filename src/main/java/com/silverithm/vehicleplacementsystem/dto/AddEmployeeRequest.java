package com.silverithm.vehicleplacementsystem.dto;

public record AddEmployeeRequest(String name, Location workPlace, Location homeAddress, int maxCapacity) {
}

