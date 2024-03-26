package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.AppUser;

public record AddElderRequest(String name, String homeAddress, boolean requireFrontSeat, Long id) {
}
