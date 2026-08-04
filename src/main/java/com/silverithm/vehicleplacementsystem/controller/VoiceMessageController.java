package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.service.VoiceMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 고충·신고 + 건의함 (VoiceBox) API.
 * 제출은 인증 사용자 누구나, 목록(scope=admin)·처리(PATCH)는 기관 관리자만.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/voice-box")
@RequiredArgsConstructor
public class VoiceMessageController {

    private final VoiceMessageService voiceMessageService;

    public record CreateRequest(String type, String title, String content, Boolean isAnonymous) {
    }

    public record UpdateRequest(String status, String adminReply) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateRequest body,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        try {
            var created = voiceMessageService.create(userDetails, body.type(), body.title(), body.content(),
                    Boolean.TRUE.equals(body.isAnonymous()));
            return ResponseEntity.ok(Map.of("success", true, "message", created));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[VoiceBox API] 접수 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "접수 중 오류가 발생했습니다."));
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(defaultValue = "mine") String scope,
                                                    @RequestParam(required = false) String type,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        try {
            var messages = "admin".equalsIgnoreCase(scope)
                    ? voiceMessageService.listForAdmin(userDetails, type)
                    : voiceMessageService.listMine(userDetails);
            return ResponseEntity.ok(Map.of("messages", messages));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[VoiceBox API] 목록 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "목록 조회 중 오류가 발생했습니다."));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody UpdateRequest body,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        try {
            var updated = voiceMessageService.update(userDetails, id, body.status(), body.adminReply());
            return ResponseEntity.ok(Map.of("success", true, "message", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[VoiceBox API] 처리 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "처리 중 오류가 발생했습니다."));
        }
    }
}
