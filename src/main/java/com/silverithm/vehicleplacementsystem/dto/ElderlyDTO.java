package com.silverithm.vehicleplacementsystem.dto;

import lombok.Getter;

public record ElderlyDTO(Long id, String name, Location homeAddress, boolean requiredFrontSeat) {
}
