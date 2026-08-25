package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.CreateMeetingMinutesRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.MeetingMinutesAudioChunkRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.MeetingMinutesDTO;
import com.silverithm.vehicleplacementsystem.dto.MeetingMinutesSignRequestDTO;
import com.silverithm.vehicleplacementsystem.service.MeetingMinutesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 회의록 API.
 * 호출자 신원은 JWT(@AuthenticationPrincipal)에서 해석하고,
 * companyId 파라미터는 CompanyScopeInterceptor가, id 기반 요청은 서비스의
 * requireSameCompany가 회사 경계를 지킨다.
 */
@RestController
@RequestMapping("/api/v1/meeting-minutes")
@RequiredArgsConstructor
@Slf4j
@Validated
public class MeetingMinutesController {

    private final MeetingMinutesService minutesService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long companyId) {
        return handle("목록", () -> {
            List<MeetingMinutesDTO> items = minutesService.list(companyId, userDetails);
            return ResponseEntity.ok(Map.of("items", items));
        });
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return handle("상세", () ->
                ResponseEntity.ok(Map.of("minutes", minutesService.get(id, userDetails))));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long companyId,
            @Valid @RequestBody CreateMeetingMinutesRequestDTO dto) {
        return handle("생성", () ->
                ResponseEntity.ok(Map.of("minutes", minutesService.create(companyId, userDetails, dto))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody CreateMeetingMinutesRequestDTO dto) {
        return handle("수정", () ->
                ResponseEntity.ok(Map.of("minutes", minutesService.update(id, userDetails, dto))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return handle("삭제", () -> {
            minutesService.delete(id, userDetails);
            return ResponseEntity.ok(Map.of("success", true));
        });
    }

    /** 등록 — 참석자에게 서명 요청 푸시가 나간다 */
    @PostMapping("/{id}/register")
    public ResponseEntity<Map<String, Object>> register(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return handle("등록", () ->
                ResponseEntity.ok(Map.of("minutes", minutesService.register(id, userDetails))));
    }

    /** 미서명자 재알림 */
    @PostMapping("/{id}/remind")
    public ResponseEntity<Map<String, Object>> remind(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return handle("재알림", () ->
                ResponseEntity.ok(Map.of("minutes", minutesService.remind(id, userDetails))));
    }

    /** 참석자 본인 서명 (signatureBase64 없으면 등록 서명 자동 사용) */
    @PostMapping("/{id}/attendees/{attendeeId}/sign")
    public ResponseEntity<Map<String, Object>> sign(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long attendeeId,
            @RequestBody(required = false) MeetingMinutesSignRequestDTO dto) {
        return handle("서명", () -> ResponseEntity.ok(Map.of("minutes",
                minutesService.signSelf(id, attendeeId, userDetails,
                        dto != null ? dto.getSignatureBase64() : null))));
    }

    /** 입회 서명 — 관리자/작성자 화면에서 참석자가 직접 그린다 (서명 이미지 필수) */
    @PostMapping("/{id}/attendees/{attendeeId}/guest-sign")
    public ResponseEntity<Map<String, Object>> guestSign(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long attendeeId,
            @RequestBody MeetingMinutesSignRequestDTO dto) {
        return handle("입회 서명", () -> ResponseEntity.ok(Map.of("minutes",
                minutesService.guestSign(id, attendeeId, userDetails, dto.getSignatureBase64()))));
    }

    /** 완료 — 결재함에 완결 문서로 등록 (멱등) */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> complete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return handle("완료", () ->
                ResponseEntity.ok(Map.of("minutes", minutesService.complete(id, userDetails))));
    }

    /** 실시간 전사문 주기 저장 (누적 전문을 통째로 받는다) */
    @PutMapping("/{id}/transcript")
    public ResponseEntity<Map<String, Object>> saveTranscript(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return handle("전사 저장", () -> {
            minutesService.saveTranscript(id, userDetails, body.get("transcript"));
            return ResponseEntity.ok(Map.of("success", true));
        });
    }

    /** 녹음 조각 등록 (파일은 /files/upload category=meetings로 먼저 올린다) */
    @PostMapping("/{id}/audio-chunks")
    public ResponseEntity<Map<String, Object>> addAudioChunk(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody MeetingMinutesAudioChunkRequestDTO dto) {
        return handle("녹음 조각", () -> {
            minutesService.addAudioChunk(id, userDetails, dto);
            return ResponseEntity.ok(Map.of("success", true));
        });
    }

    /** 기관 양식(섹션 구성) 조회 — 커스텀이 없으면 기본값 */
    @GetMapping("/template")
    public ResponseEntity<Map<String, String>> getTemplate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long companyId) {
        return handleTemplate(() ->
                ResponseEntity.ok(minutesService.getTemplate(companyId, userDetails)));
    }

    /** 기관 양식 저장 (관리자만) */
    @PutMapping("/template")
    public ResponseEntity<Map<String, String>> saveTemplate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long companyId,
            @RequestBody Map<String, String> body) {
        return handleTemplate(() ->
                ResponseEntity.ok(minutesService.saveTemplate(companyId, userDetails, body.get("sections"))));
    }

    private ResponseEntity<Map<String, Object>> handle(String action,
                                                       Supplier<ResponseEntity<Map<String, Object>>> work) {
        try {
            return work.get();
        } catch (SecurityException e) {
            log.warn("[MeetingMinutes API] {} 권한 거부: {}", action, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 잘못 보낸 요청이지 서버 잘못이 아니다 — 클라이언트가 메시지를 그대로 보여줄 수 있게 400
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[MeetingMinutes API] {} 오류:", action, e);
            return ResponseEntity.internalServerError().body(Map.of("error", action + " 처리 중 오류가 발생했습니다."));
        }
    }

    private ResponseEntity<Map<String, String>> handleTemplate(Supplier<ResponseEntity<Map<String, String>>> work) {
        try {
            return work.get();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[MeetingMinutes API] 양식 처리 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "양식 처리 중 오류가 발생했습니다."));
        }
    }
}
