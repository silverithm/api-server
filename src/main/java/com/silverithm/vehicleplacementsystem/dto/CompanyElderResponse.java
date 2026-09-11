package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Elderly;

/**
 * 기관 어르신 목록 응답.
 *
 * <p>{@link ElderlyDTO}는 배차 계산 코드가 널리 쓰는 계약이라 건드리지 않고,
 * 케어 정보가 붙는 화면용 응답만 따로 둔다. 케어 정보가 없으면 {@code careProfile}은 null이다.
 */
public record CompanyElderResponse(
        Long id,
        String name,
        Location homeAddress,
        boolean requiredFrontSeat,
        String homeAddressName,
        ElderCareProfileDTO careProfile
) {

    public static CompanyElderResponse from(Elderly elderly) {
        return new CompanyElderResponse(
                elderly.getId(),
                elderly.getName(),
                elderly.getHomeAddress(),
                elderly.isRequiredFrontSeat(),
                elderly.getHomeAddressName(),
                ElderCareProfileDTO.from(elderly.getCareProfile()));
    }
}
