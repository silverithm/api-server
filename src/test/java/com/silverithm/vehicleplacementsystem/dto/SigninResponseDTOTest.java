package com.silverithm.vehicleplacementsystem.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.util.AdminDisplay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 로그인 응답에 이메일·직책이 실리는지.
 *
 * 앱은 이 응답만 보고 세션 사용자를 만드는데, 예전에는 응답에 이메일·직책 칸 자체가
 * 없어서 관리자 세션에서 이메일이 비고("자동로그인 직후 이메일이 안 보인다") 직책 대신
 * 늘 '관리자'만 떴다.
 */
class SigninResponseDTOTest {

    private TokenInfo tokenInfo() {
        return TokenInfo.builder().accessToken("access").refreshToken("refresh").build();
    }

    @Test
    @DisplayName("사진·이메일·직책을 싣지 않던 짧은 생성자는 셋 다 null로 채운다")
    void shortConstructor_defaultsToNull() {
        SigninResponseDTO response = new SigninResponseDTO(
                1L, "관리자1", 2L, "회사", null, "서울시", "CODE1",
                tokenInfo(), new SubscriptionResponseDTO(), null);

        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.userEmail()).isNull();
        assertThat(response.position()).isNull();
    }

    @Test
    @DisplayName("전체 생성자는 이메일·직책을 그대로 나른다")
    void fullConstructor_carriesEmailAndPosition() {
        SigninResponseDTO response = new SigninResponseDTO(
                1L, "관리자1", 2L, "회사", null, "서울시", "CODE1",
                tokenInfo(), new SubscriptionResponseDTO(), null,
                "https://example.com/p.jpg", "admin@carev.kr", "원장");

        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/p.jpg");
        assertThat(response.userEmail()).isEqualTo("admin@carev.kr");
        assertThat(response.position()).isEqualTo("원장");
    }

    @Test
    @DisplayName("AdminDisplay.position: 직책을 안 정했으면 '관리자'로 확정된다")
    void adminDisplayPosition_defaultsWhenBlank() {
        AppUser appUser = new AppUser();

        assertThat(AdminDisplay.position(appUser)).isEqualTo("관리자");
    }

    @Test
    @DisplayName("AdminDisplay.position: 직책을 정했으면 그 값이 확정된다")
    void adminDisplayPosition_usesSetValue() throws Exception {
        AppUser appUser = new AppUser();
        java.lang.reflect.Field field = AppUser.class.getDeclaredField("position");
        field.setAccessible(true);
        field.set(appUser, "원장");

        assertThat(AdminDisplay.position(appUser)).isEqualTo("원장");
    }
}
