package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.service.SignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 결재 서명 관리 API. AppUser·Member 공용 (JWT principal 기준 "내 서명").
 */
@RestController
@RequestMapping("/api/v1/signatures")
@RequiredArgsConstructor
@Slf4j
public class SignatureController {

    private final SignatureService signatureService;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMySignature(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String signatureUrl = signatureService.getMySignatureUrl(userDetails);
            Map<String, Object> body = new HashMap<>();
            body.put("signatureUrl", signatureUrl);
            return ResponseEntity.ok().headers(getCorsHeaders()).body(body);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Signature API] 서명 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "서명 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> registerMySignature(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String signatureUrl = signatureService.registerMySignature(userDetails, body.get("imageBase64"));
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "signatureUrl", signatureUrl,
                            "message", "서명이 등록되었습니다."
                    ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Signature API] 서명 등록 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "서명 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteMySignature(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            signatureService.deleteMySignature(userDetails);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "message", "서명이 삭제되었습니다."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Signature API] 서명 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "서명 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok().headers(getCorsHeaders()).build();
    }

    private HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        return headers;
    }
}
