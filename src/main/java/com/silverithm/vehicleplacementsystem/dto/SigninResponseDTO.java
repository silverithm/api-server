package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;

public record SigninResponseDTO(Long userId, String userName, Long companyId, String companyName,
                                Location companyAddress,
                                String companyAddressName,
                                String companyCode,
                                TokenInfo tokenInfo, SubscriptionResponseDTO subscription, String customerKey,
                                /**
                                 * 관리자 프로필 사진 — 없으면 null (화면은 이니셜로 대체한다).
                                 * 앱은 로그인 응답만 보고 사람을 그리는데 이 값이 없어서 관리자만
                                 * 늘 이니셜로 떴다. 웹은 별도 조회가 있어 티가 나지 않았다.
                                 */
                                String profileImageUrl,
                                /**
                                 * 관리자 이메일. 없으면 앱이 로그인 시 입력한 값으로 채운다(구버전 대응).
                                 */
                                String userEmail,
                                /**
                                 * 관리자 직책 — 정해두지 않았으면 AdminDisplay.DEFAULT_POSITION('관리자').
                                 * 앱/웹이 같은 규칙(AdminDisplay)으로 표기하도록 값 자체를 여기서 확정해 내려준다.
                                 */
                                String position) {

    /** 사진·이메일·직책을 싣지 않던 호출부를 위한 짧은 생성자 */
    public SigninResponseDTO(Long userId, String userName, Long companyId, String companyName,
                             Location companyAddress, String companyAddressName, String companyCode,
                             TokenInfo tokenInfo, SubscriptionResponseDTO subscription, String customerKey) {
        this(userId, userName, companyId, companyName, companyAddress, companyAddressName, companyCode,
                tokenInfo, subscription, customerKey, null, null, null);
    }
}
