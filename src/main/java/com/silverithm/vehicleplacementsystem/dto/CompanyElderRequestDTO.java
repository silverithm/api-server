package com.silverithm.vehicleplacementsystem.dto;

public record CompanyElderRequestDTO(
        String name,
        String homeAddress,
        boolean requiredFrontSeat
) {
}
