package com.silverithm.vehicleplacementsystem.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("로그 개인정보 마스킹")
class PrivacyMaskTest {

    @Test
    @DisplayName("이메일은 로컬파트를 가리고 도메인만 남긴다")
    void maskEmail() {
        assertThat(PrivacyMask.email("hongildong@example.com")).isEqualTo("ho********@example.com");
        assertThat(PrivacyMask.email("ab@example.com")).isEqualTo("a*@example.com");
        assertThat(PrivacyMask.email(null)).isEqualTo("-");
        assertThat(PrivacyMask.email("   ")).isEqualTo("-");
    }

    @Test
    @DisplayName("@가 없으면 성명 규칙으로 가린다")
    void maskEmailWithoutAt() {
        assertThat(PrivacyMask.email("hongildong")).isEqualTo("h********g");
    }

    @Test
    @DisplayName("성명은 첫 글자와 끝 글자만 남긴다")
    void maskName() {
        assertThat(PrivacyMask.name("홍길동")).isEqualTo("홍*동");
        assertThat(PrivacyMask.name("김철")).isEqualTo("김*");
        assertThat(PrivacyMask.name("김")).isEqualTo("*");
        assertThat(PrivacyMask.name(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("연락처는 국번을 가린다")
    void maskPhone() {
        assertThat(PrivacyMask.phone("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(PrivacyMask.phone("01012345678")).isEqualTo("010-****-5678");
        assertThat(PrivacyMask.phone(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("마스킹 결과에 원본 개인정보가 남지 않는다")
    void maskedValueDoesNotLeakOriginal() {
        assertThat(PrivacyMask.email("hongildong@example.com")).doesNotContain("hongildong");
        assertThat(PrivacyMask.name("홍길동")).doesNotContain("길");
        assertThat(PrivacyMask.phone("010-1234-5678")).doesNotContain("1234");
    }
}
