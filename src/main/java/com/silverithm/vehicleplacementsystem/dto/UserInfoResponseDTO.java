package com.silverithm.vehicleplacementsystem.dto;

public record UserInfoResponseDTO(Long userId, String userName, String userEmail, Long companyId, String companyName,
                                Location companyAddress,
                                String companyAddressName,
                                SubscriptionResponseDTO subscription, String customerKey) {
}