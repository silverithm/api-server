package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.service.PasswordResetService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 비밀번호 재설정 링크의 두 번째 절반 — 메일의 링크로 들어온 사람이 새 비밀번호를 정한다 */
@RestController
@RequestMapping("/api/v1/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public record ConfirmRequest(String token, String newPassword) {
    }

    /** 링크가 아직 유효한지 — 만료된 링크로 입력 칸을 보여 주지 않으려고 */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Boolean>> check(@RequestParam String token) {
        return ResponseEntity.ok(Map.of("valid", passwordResetService.isTokenValid(token)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(@RequestBody ConfirmRequest request) {
        passwordResetService.confirm(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "비밀번호를 바꿨습니다. 새 비밀번호로 로그인해 주세요."));
    }
}
