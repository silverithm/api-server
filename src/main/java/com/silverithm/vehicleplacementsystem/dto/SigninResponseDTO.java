package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;

public record SigninResponseDTO(Long userId, String userName, Long companyId, String companyName,
                                Location companyAddress,
                                String companyAddressName,
                                String companyCode,
                                TokenInfo tokenInfo, SubscriptionResponseDTO subscription, String customerKey) {
}
