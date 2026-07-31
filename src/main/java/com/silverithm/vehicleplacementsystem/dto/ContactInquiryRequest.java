package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공개 페이지(문의하기·제휴 광고)에서 들어오는 문의.
 *
 * <p>비로그인 사용자가 보내므로 길이를 제한한다. 메일 헤더 인젝션을 막기 위해
 * 제목에 들어가는 값(이름·유형)은 서비스에서 개행을 제거한다.
 */
public record ContactInquiryRequest(
        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이내로 입력해주세요.")
        String name,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일은 100자 이내로 입력해주세요.")
        String email,

        @Size(max = 100, message = "기관명은 100자 이내로 입력해주세요.")
        String organization,

        @Size(max = 30, message = "연락처는 30자 이내로 입력해주세요.")
        String phone,

        @Size(max = 40, message = "문의 유형은 40자 이내로 입력해주세요.")
        String inquiryType,

        @NotBlank(message = "문의 내용을 입력해주세요.")
        @Size(max = 5000, message = "문의 내용은 5000자 이내로 입력해주세요.")
        String message
) {
}
