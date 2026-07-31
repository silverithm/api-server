package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 승인 요청 body (선택). 앱은 body 없이 호출하므로 required=false로 바인딩된다.
 * signatureBase64가 없으면 결재자의 등록 서명을 사용하고, 그것도 없으면 서명 없이 승인한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApproveRequestDTO {

    // data URL(data:image/png;base64,...) 또는 순수 base64 PNG
    private String signatureBase64;
}
