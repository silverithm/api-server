package com.silverithm.vehicleplacementsystem.dto;

import lombok.Getter;

public record ElderlyDTO(Long id, Location homeAddress, boolean requiredFrontSeat) {
}