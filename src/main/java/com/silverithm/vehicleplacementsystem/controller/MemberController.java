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
    
    /**
     * 회사 목록 조회
     */
    @GetMapping("/companies")
    public ResponseEntity<Map<String, List<CompanyListDTO>>> getAllCompanies() {
        try {
            log.info("[Member API] 회사 목록 조회 요청");
            
            List<CompanyListDTO> companies = memberService.getAllCompanies();
            
            return ResponseEntity.ok(Map.of("companies", companies));
            
        } catch (Exception e) {
            log.error("[Member API] 회사 목록 조회 오류:", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 회원가입 요청
     */
    @PostMapping("/join-request")
    public ResponseEntity<MemberJoinRequestResponseDTO> submitJoinRequest(
            @Valid @RequestBody MemberJoinRequestDTO requestDTO) {
        
        try {
            log.info("[Member API] 회원가입 요청: {}", requestDTO.getUsername());
            
            MemberJoinRequestResponseDTO response = memberService.submitJoinRequest(requestDTO);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원가입 요청 오류: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[Member API] 회원가입 요청 서버 오류:", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 모든 가입 요청 조회
     */
    @GetMapping("/join-requests")
    public ResponseEntity<Map<String, List<MemberJoinRequestResponseDTO>>> getAllJoinRequests() {
        try {
            log.info("[Member API] 모든 가입 요청 조회");
            
            List<MemberJoinRequestResponseDTO> requests = memberService.getAllJoinRequests();
            
            return ResponseEntity.ok(Map.of("requests", requests));
            
        } catch (Exception e) {
            log.error("[Member API] 가입 요청 조회 오류:", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 대기중인 가입 요청 조회
     */
    @GetMapping("/join-requests/pending")
    public ResponseEntity<Map<String, List<MemberJoinRequestResponseDTO>>> getPendingJoinRequests() {
        try {
            log.info("[Member API] 대기중인 가입 요청 조회");
            
            List<MemberJoinRequestResponseDTO> requests = memberService.getPendingJoinRequests();
            
            return ResponseEntity.ok(Map.of("requests", requests));
            
        } catch (Exception e) {
            log.error("[Member API] 대기중인 가입 요청 조회 오류:", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 가입 요청 승인
     */
    @PutMapping("/join-requests/{id}/approve")
    public ResponseEntity<Map<String, String>> approveJoinRequest(
            @PathVariable Long id,
            @RequestParam Long adminId) {
        
        try {
            log.info("[Member API] 가입 요청 승인: requestId={}, adminId={}", id, adminId);
            
            memberService.approveJoinRequest(id, adminId);
            
            return ResponseEntity.ok(Map.of("message", "가입 요청이 승인되었습니다"));
            
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 가입 승인 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Member API] 가입 승인 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "가입 승인 중 오류가 발생했습니다"));
        }
    }
    
    /**
     * 가입 요청 거부
     */
    @PutMapping("/join-requests/{id}/reject")
    public ResponseEntity<Map<String, String>> rejectJoinRequest(
            @PathVariable Long id,
            @RequestParam Long adminId,
            @Valid @RequestBody MemberJoinRequestProcessDTO processDTO) {
        
        try {
            log.info("[Member API] 가입 요청 거부: requestId={}, adminId={}", id, adminId);
            
            memberService.rejectJoinRequest(id, adminId, processDTO);
            
            return ResponseEntity.ok(Map.of("message", "가입 요청이 거부되었습니다"));
            
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 가입 거부 오류: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Member API] 가입 거부 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "가입 거부 중 오류가 발생했습니다"));
        }
    }
    
    /**
     * 회원 목록 조회 (역할/상태 필터 지원)
     */
    @GetMapping
    public ResponseEntity<Map<String, List<MemberDTO>>> getMembers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        
        try {
            log.info("[Member API] 회원 목록 조회: role={}, status={}", role, status);
            
            List<MemberDTO> members;
            
            if (role != null) {
                members = memberService.getMembersByRole(role);
            } else if (status != null) {
                members = memberService.getMembersByStatus(status);
            } else {
                members = memberService.getAllMembers();
            }
            
            return ResponseEntity.ok(Map.of("members", members));
            
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원 조회 오류: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[Member API] 회원 조회 서버 오류:", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 특정 회원 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<MemberDTO> getMemberById(@PathVariable Long id) {
        try {
            log.info("[Member API] 회원 단건 조회: id={}", id);
            
            MemberDTO member = memberService.getMemberById(id);
            
            return ResponseEntity.ok(member);
            
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원 조회 오류: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("[Member API] 회원 조회 서버 오류:", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 회원 정보 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<MemberDTO> updateMember(
            @PathVariable Long id,
            @Valid @RequestBody MemberUpdateRequestDTO updateDTO) {
        
        try {
            log.info("[Member API] 회원 정보 수정: id={}", id);
            
            MemberDTO updatedMember = memberService.updateMember(id, updateDTO);
            
            return ResponseEntity.ok(updatedMember);
            
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원 수정 오류: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[Member API] 회원 수정 서버 오류:", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 회원 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteMember(@PathVariable Long id) {
        try {
            log.info("[Member API] 회원 삭제: id={}", id);
            
            memberService.deleteMember(id);
            
            return ResponseEntity.ok(Map.of("message", "회원이 삭제되었습니다"));
            
        } catch (IllegalArgumentException e) {
            log.error("[Member API] 회원 삭제 오류: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("[Member API] 회원 삭제 서버 오류:", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "회원 삭제 중 오류가 발생했습니다"));
        }
    }
} 