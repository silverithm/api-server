package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;

public record SigninResponseDTO(Long userId, String userName, String companyName, Location companyAddress,
                                String companyAddressName,
                                TokenInfo tokenInfo) {
}
