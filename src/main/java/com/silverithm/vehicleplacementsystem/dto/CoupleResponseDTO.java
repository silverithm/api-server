package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Couple;
import java.util.List;

public record CoupleResponseDTO(ElderlyDTO elder1, ElderlyDTO elder2) {
    public static List<CoupleResponseDTO> from(List<Couple> couples) {
        return couples.stream()
                .map(couple -> new CoupleResponseDTO(
                        ElderlyDTO.from(couple.getElder1()),
                        ElderlyDTO.from(couple.getElder2())
                ))
                .toList();
    }
}
