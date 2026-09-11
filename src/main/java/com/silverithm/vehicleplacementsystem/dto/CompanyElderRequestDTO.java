package com.silverithm.vehicleplacementsystem.dto;

/**
 * 기관 어르신 등록·수정 요청.
 *
 * <p>{@code careProfile}이 없으면(null) 케어 정보는 건드리지 않는다 — 이름만 고치는
 * 기존 화면이 케어 정보를 통째로 지우면 안 되기 때문이다.
 */
public record CompanyElderRequestDTO(
        String name,
        String homeAddress,
        boolean requiredFrontSeat,
        ElderCareProfileRequest careProfile
) {
}
