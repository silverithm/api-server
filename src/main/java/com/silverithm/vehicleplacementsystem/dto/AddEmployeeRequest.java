package com.silverithm.vehicleplacementsystem.dto;

public record AddEmployeeRequest(String name, String workPlace, String homeAddress, int maxCapacity, Boolean isDriver) {
}

