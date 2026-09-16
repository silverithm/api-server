package com.silverithm.vehicleplacementsystem.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포된 Flutter 앱 옛 버전이 /v1 없는 경로(/api/dispatch-settings/driver-roles)로 호출해 5일간
 * 33건 404가 났다. 새 앱이 완전히 깔릴 때까지 구경로도 그대로 받아 {@link DispatchSettingController}의
 * 로직으로 넘긴다. DispatchSettingController에 @RequestMapping("/api/v1/dispatch-settings")가 이미
 * 붙어 있어 그 안에서 이 절대경로를 함께 매핑할 수 없어 별도 컨트롤러로 둔다.
 * 보안 설정(WebSecurityConfigure)도 /v1 경로와 동일하게 permitAll 처리한다.
 */
@RestController
@RequestMapping("/api/dispatch-settings")
@RequiredArgsConstructor
public class DispatchSettingLegacyController {

    private final DispatchSettingController dispatchSettingController;

    @GetMapping("/driver-roles")
    public ResponseEntity<?> getDriverRoles(@RequestParam Long companyId, @RequestParam String memberName) {
        return dispatchSettingController.getDriverRolesInternal(companyId, memberName);
    }
}
