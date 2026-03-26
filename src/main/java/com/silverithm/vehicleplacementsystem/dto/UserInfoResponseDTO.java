package com.silverithm.vehicleplacementsystem.dto;

public record UserInfoResponseDTO(Long userId, String userName, String userEmail, Long companyId, String companyName,
                                Location companyAddress,
                                String companyAddressName,
                                String companyCode,
                                SubscriptionResponseDTO subscription, String customerKey) {
}
