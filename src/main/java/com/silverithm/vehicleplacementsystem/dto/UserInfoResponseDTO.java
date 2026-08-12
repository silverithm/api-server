package com.silverithm.vehicleplacementsystem.dto;

public record UserInfoResponseDTO(Long userId, String userName, String userEmail, Long companyId, String companyName,
                                Location companyAddress,
                                String companyAddressName,
                                String companyCode,
                                SubscriptionResponseDTO subscription, String customerKey,
                                String companySealUrl,
                                String companyHomepageUrl,
                                /** 관리자 직책 — 정해두지 않았으면 null (화면에서는 '관리자'로 보인다) */
                                String position,
                                Long positionId,
                                /** 관리자 프로필 사진 — 없으면 null (화면은 이니셜로 대체한다) */
                                String profileImageUrl) {
}
