package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.PositionDTO;
import com.silverithm.vehicleplacementsystem.dto.PositionRequestDTO;
import com.silverithm.vehicleplacementsystem.service.PositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
@Slf4j
@Validated
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPositions(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyCode) {
        try {
            log.info("[Position API] 직책 목록 조회: companyId={}, companyCode={}", companyId, companyCode);
            List<PositionDTO> positions = positionService.getPositions(companyId, companyCode);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "positions", positions));
        } catch (RuntimeException e) {
            log.error("[Position API] 직책 목록 조회 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Position API] 직책 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "직책 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPosition(
            @RequestParam Long companyId,
            @Valid @RequestBody PositionRequestDTO request) {
        try {
            log.info("[Position API] 직책 등록: companyId={}, name={}", companyId, request.getName());
            PositionDTO position = positionService.createPosition(companyId, request);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "position", position, "message", "직책이 등록되었습니다."));
        } catch (RuntimeException e) {
            log.error("[Position API] 직책 등록 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Position API] 직책 등록 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "직책 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePosition(
            @PathVariable Long id,
            @Valid @RequestBody PositionRequestDTO request) {
        try {
            log.info("[Position API] 직책 수정: id={}", id);
            PositionDTO position = positionService.updatePosition(id, request);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "position", position, "message", "직책이 수정되었습니다."));
        } catch (RuntimeException e) {
            log.error("[Position API] 직책 수정 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Position API] 직책 수정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "직책 수정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePosition(@PathVariable Long id) {
        try {
            log.info("[Position API] 직책 삭제: id={}", id);
            positionService.deletePosition(id);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "message", "직책이 삭제되었습니다."));
        } catch (RuntimeException e) {
            log.error("[Position API] 직책 삭제 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Position API] 직책 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "직책 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PutMapping("/assign")
    public ResponseEntity<Map<String, Object>> assignPositionToMember(
            @RequestParam Long memberId,
            @RequestParam(required = false) Long positionId) {
        try {
            log.info("[Position API] 직책 배정: memberId={}, positionId={}", memberId, positionId);
            positionService.assignPositionToMember(memberId, positionId);
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "message", "직책이 배정되었습니다."));
        } catch (RuntimeException e) {
            log.error("[Position API] 직책 배정 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Position API] 직책 배정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "직책 배정 중 오류가 발생했습니다: " + e.getMessage()));
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
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        return headers;
    }
}
