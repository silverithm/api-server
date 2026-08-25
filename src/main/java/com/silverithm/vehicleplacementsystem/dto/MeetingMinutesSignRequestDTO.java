package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 참석자 서명 요청.
 * signatureBase64가 있으면 즉석 서명 날인, 없으면 등록 서명 자동 사용 (결재 승인과 같은 계약).
 * 입회 서명(guest-sign)에서는 필수다 — 외부인은 등록 서명이 없다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinutesSignRequestDTO {

    private String signatureBase64;
}
