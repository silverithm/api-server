package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.service.MemberService;
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
@RequestMapping("/api/members")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Validated
public class MemberController {
    
    private final MemberService memberService;
    
    // 회원가입 요청
    @PostMapping("/join-request")
    public ResponseEntity<Map<String, Object>> submitJoinRequest(
            @Valid @RequestBody MemberJoinRequestDTO requestDTO) {
        
        try {
            log.info("[Member API] 회원가입 요청: username={}", requestDTO.getUsername());
            
            MemberJoinRequestResponseDTO result = memberService.submitJoinRequest(requestDTO);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "회원가입 요청이 완료되었습니다. 관리자 승인을 기다려주세요.",
                            "data", result
                    ));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원가입 요청 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Member API] 회원가입 요청 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "회원가입 요청 처리 중 오류가 발생했습니다"));
        }
    }
    
    // 관리자용 - 모든 가입 요청 조회
    @GetMapping("/join-requests")
    public ResponseEntity<Map<String, List<MemberJoinRequestResponseDTO>>> getAllJoinRequests() {
        try {
            log.info("[Member API] 모든 가입 요청 조회");
            
            List<MemberJoinRequestResponseDTO> requests = memberService.getAllJoinRequests();
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("requests", requests));
                    
        } catch (Exception e) {
            log.error("[Member API] 가입 요청 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    // 관리자용 - 대기중인 가입 요청 조회
    @GetMapping("/join-requests/pending")
    public ResponseEntity<Map<String, List<MemberJoinRequestResponseDTO>>> getPendingJoinRequests() {
        try {
            log.info("[Member API] 대기중인 가입 요청 조회");
            
            List<MemberJoinRequestResponseDTO> requests = memberService.getPendingJoinRequests();
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("requests", requests));
                    
        } catch (Exception e) {
            log.error("[Member API] 대기중인 가입 요청 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    // 관리자용 - 가입 요청 승인
    @PutMapping("/join-requests/{id}/approve")
    public ResponseEntity<Map<String, String>> approveJoinRequest(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long adminId) {
        
        try {
            log.info("[Member API] 가입 요청 승인: id={}, adminId={}", id, adminId);
            
            memberService.approveJoinRequest(id, adminId);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("message", "가입 요청이 승인되었습니다"));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 가입 승인 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Member API] 가입 승인 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "가입 승인 중 오류가 발생했습니다"));
        }
    }
    
    // 관리자용 - 가입 요청 거부
    @PutMapping("/join-requests/{id}/reject")
    public ResponseEntity<Map<String, String>> rejectJoinRequest(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long adminId,
            @RequestBody(required = false) MemberJoinRequestProcessDTO processDTO) {
        
        try {
            log.info("[Member API] 가입 요청 거부: id={}, adminId={}", id, adminId);
            
            if (processDTO == null) {
                processDTO = new MemberJoinRequestProcessDTO();
            }
            
            memberService.rejectJoinRequest(id, adminId, processDTO);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("message", "가입 요청이 거부되었습니다"));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 가입 거부 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Member API] 가입 거부 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "가입 거부 중 오류가 발생했습니다"));
        }
    }
    
    // 모든 회원 조회
    @GetMapping
    public ResponseEntity<?> getAllMembers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        
        try {
            log.info("[Member API] 회원 조회: role={}, status={}", role, status);
            
            List<MemberDTO> members;
            
            if (role != null) {
                members = memberService.getMembersByRole(role);
            } else if (status != null) {
                members = memberService.getMembersByStatus(status);
            } else {
                members = memberService.getAllMembers();
            }
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("members", members));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원 조회 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Member API] 회원 조회 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .build();
        }
    }
    
    // 특정 회원 조회
    @GetMapping("/{id}")
    public ResponseEntity<?> getMemberById(@PathVariable Long id) {
        try {
            log.info("[Member API] 회원 단건 조회: id={}", id);
            
            MemberDTO member = memberService.getMemberById(id);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("member", member));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원 조회 오류: {}", e.getMessage());
            return ResponseEntity.notFound()
                    .headers(getCorsHeaders())
                    .build();
        } catch (Exception e) {
            log.error("[Member API] 회원 조회 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "회원 조회 중 오류가 발생했습니다"));
        }
    }
    
    // 회원 정보 수정
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateMember(
            @PathVariable Long id,
            @Valid @RequestBody MemberUpdateRequestDTO updateDTO) {
        
        try {
            log.info("[Member API] 회원 정보 수정: id={}", id);
            
            MemberDTO updated = memberService.updateMember(id, updateDTO);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "회원 정보가 수정되었습니다",
                            "member", updated
                    ));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원 수정 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Member API] 회원 수정 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "회원 정보 수정 중 오류가 발생했습니다"));
        }
    }
    
    // 회원 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteMember(@PathVariable Long id) {
        try {
            log.info("[Member API] 회원 삭제: id={}", id);
            
            memberService.deleteMember(id);
            
            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("message", "회원이 삭제되었습니다"));
                    
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원 삭제 오류: {}", e.getMessage());
            return ResponseEntity.notFound()
                    .headers(getCorsHeaders())
                    .build();
        } catch (Exception e) {
            log.error("[Member API] 회원 삭제 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "회원 삭제 중 오류가 발생했습니다"));
        }
    }
    
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .headers(getCorsHeaders())
                .build();
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