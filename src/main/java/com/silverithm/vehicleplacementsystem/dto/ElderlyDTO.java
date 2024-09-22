package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Elderly;

public record ElderlyDTO(Long id, String name, Location homeAddress, boolean requiredFrontSeat,
                         String homeAddressName) {

    public static ElderlyDTO from(Elderly elderly) {
        return new ElderlyDTO(elderly.getId(), elderly.getName(), elderly.getHomeAddress(),
                elderly.isRequiredFrontSeat(),
                elderly.getHomeAddressName());
    }
}
