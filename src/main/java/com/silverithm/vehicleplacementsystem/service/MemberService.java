package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.MemberJoinRequest;
import com.silverithm.vehicleplacementsystem.entity.Notification;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberJoinRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {
    
    private final MemberRepository memberRepository;
    private final MemberJoinRequestRepository memberJoinRequestRepository;
    private final CompanyRepository companyRepository;
    private final NotificationService notificationService;
    
    public List<CompanyListDTO> getAllCompanies() {
        log.info("[Member Service] 모든 회사 조회");
        
        List<Company> companies = companyRepository.findAll();
        
        return companies.stream()
                .map(CompanyListDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public MemberJoinRequestResponseDTO submitJoinRequest(MemberJoinRequestDTO requestDTO) {
        log.info("[Member Service] 회원가입 요청: username={}, email={}, companyId={}", 
                requestDTO.getUsername(), requestDTO.getEmail(), requestDTO.getCompanyId());
        
        // 중복 확인
        if (memberRepository.existsByUsername(requestDTO.getUsername())) {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다: " + requestDTO.getUsername());
        }
        
        if (memberRepository.existsByEmail(requestDTO.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + requestDTO.getEmail());
        }
        
        if (memberJoinRequestRepository.existsByUsername(requestDTO.getUsername())) {
            throw new IllegalArgumentException("이미 가입 요청된 사용자명입니다: " + requestDTO.getUsername());
        }
        
        if (memberJoinRequestRepository.existsByEmail(requestDTO.getEmail())) {
            throw new IllegalArgumentException("이미 가입 요청된 이메일입니다: " + requestDTO.getEmail());
        }
        
        // 회사 검증
        Company company = null;
        if (requestDTO.getCompanyId() != null) {
            company = companyRepository.findById(requestDTO.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + requestDTO.getCompanyId()));
        }
        
        // Role enum 변환
        Member.Role role;
        try {
            role = Member.Role.valueOf(requestDTO.getRequestedRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("잘못된 역할입니다: " + requestDTO.getRequestedRole());
        }
        
        // 관리자 역할은 직접 요청할 수 없음
        if (role == Member.Role.ADMIN) {
            throw new IllegalArgumentException("관리자 역할은 직접 요청할 수 없습니다");
        }
        
        // 가입 요청 생성
        MemberJoinRequest joinRequest = MemberJoinRequest.builder()
                .username(requestDTO.getUsername())
                .password(requestDTO.getPassword()) // 실제 환경에서는 암호화 필요
                .name(requestDTO.getName())
                .email(requestDTO.getEmail())
                .phoneNumber(requestDTO.getPhoneNumber())
                .requestedRole(role)
                .department(requestDTO.getDepartment())
                .position(requestDTO.getPosition())
                .fcmToken(requestDTO.getFcmToken())
                .company(company)
                .status(MemberJoinRequest.RequestStatus.PENDING)
                .build();
        
        MemberJoinRequest saved = memberJoinRequestRepository.save(joinRequest);
        
        log.info("[Member Service] 회원가입 요청 생성 완료: ID={}", saved.getId());
        
        // 관리자에게 알림 전송
        try {
            sendJoinRequestNotificationToAdmins(saved);
        } catch (Exception e) {
            log.error("[Member Service] 관리자 알림 전송 실패: {}", e.getMessage());
        }
        
        return MemberJoinRequestResponseDTO.fromEntity(saved);
    }
    
    public List<MemberJoinRequestResponseDTO> getAllJoinRequests() {
        log.info("[Member Service] 모든 가입 요청 조회");
        
        List<MemberJoinRequest> requests = memberJoinRequestRepository.findAllByOrderByCreatedAtDesc();
        
        return requests.stream()
                .map(MemberJoinRequestResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<MemberJoinRequestResponseDTO> getPendingJoinRequests() {
        log.info("[Member Service] 대기중인 가입 요청 조회");
        
        List<MemberJoinRequest> requests = memberJoinRequestRepository.findByStatusOrderByCreatedAtDesc(
                MemberJoinRequest.RequestStatus.PENDING);
        
        return requests.stream()
                .map(MemberJoinRequestResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void approveJoinRequest(Long requestId, Long adminId) {
        log.info("[Member Service] 가입 요청 승인: requestId={}, adminId={}", requestId, adminId);
        
        MemberJoinRequest joinRequest = memberJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("해당 가입 요청을 찾을 수 없습니다: " + requestId));
        
        if (joinRequest.getStatus() != MemberJoinRequest.RequestStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리된 요청입니다");
        }
        
        // 최종 중복 체크 (다른 관리자가 동시에 승인했을 수 있음)
        if (memberRepository.existsByUsername(joinRequest.getUsername())) {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다");
        }
        
        if (memberRepository.existsByEmail(joinRequest.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다");
        }
        
        // 회원 생성
        Member member = Member.builder()
                .username(joinRequest.getUsername())
                .password(joinRequest.getPassword()) // 실제 환경에서는 암호화 필요
                .name(joinRequest.getName())
                .email(joinRequest.getEmail())
                .phoneNumber(joinRequest.getPhoneNumber())
                .role(joinRequest.getRequestedRole())
                .status(Member.MemberStatus.ACTIVE)
                .fcmToken(joinRequest.getFcmToken())
                .department(joinRequest.getDepartment())
                .position(joinRequest.getPosition())
                .company(joinRequest.getCompany())
                .build();
        
        Member savedMember = memberRepository.save(member);
        
        // 가입 요청 상태 업데이트
        joinRequest.setStatus(MemberJoinRequest.RequestStatus.APPROVED);
        joinRequest.setApprovedBy(adminId);
        joinRequest.setProcessedAt(LocalDateTime.now());
        memberJoinRequestRepository.save(joinRequest);
        
        log.info("[Member Service] 가입 승인 완료: memberId={}, requestId={}", savedMember.getId(), requestId);
        
        // 신청자에게 승인 알림 전송
        try {
            sendJoinApprovedNotificationToUser(joinRequest);
        } catch (Exception e) {
            log.error("[Member Service] 승인 알림 전송 실패: {}", e.getMessage());
        }
    }
    
    @Transactional
    public void rejectJoinRequest(Long requestId, Long adminId, MemberJoinRequestProcessDTO processDTO) {
        log.info("[Member Service] 가입 요청 거부: requestId={}, adminId={}", requestId, adminId);
        
        MemberJoinRequest joinRequest = memberJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("해당 가입 요청을 찾을 수 없습니다: " + requestId));
        
        if (joinRequest.getStatus() != MemberJoinRequest.RequestStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리된 요청입니다");
        }
        
        // 가입 요청 거부 처리
        joinRequest.setStatus(MemberJoinRequest.RequestStatus.REJECTED);
        joinRequest.setApprovedBy(adminId);
        joinRequest.setRejectReason(processDTO.getRejectReason());
        joinRequest.setProcessedAt(LocalDateTime.now());
        memberJoinRequestRepository.save(joinRequest);
        
        log.info("[Member Service] 가입 거부 완료: requestId={}", requestId);
        
        // 신청자에게 거부 알림 전송
        try {
            sendJoinRejectedNotificationToUser(joinRequest);
        } catch (Exception e) {
            log.error("[Member Service] 거부 알림 전송 실패: {}", e.getMessage());
        }
    }
    
    public List<MemberDTO> getAllMembers() {
        log.info("[Member Service] 모든 회원 조회");
        
        List<Member> members = memberRepository.findAll();
        
        return members.stream()
                .map(MemberDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<MemberDTO> getMembersByRole(String role) {
        log.info("[Member Service] 역할별 회원 조회: role={}", role);
        
        Member.Role memberRole = Member.Role.valueOf(role.toUpperCase());
        List<Member> members = memberRepository.findByRole(memberRole);
        
        return members.stream()
                .map(MemberDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<MemberDTO> getMembersByStatus(String status) {
        log.info("[Member Service] 상태별 회원 조회: status={}", status);
        
        Member.MemberStatus memberStatus = Member.MemberStatus.valueOf(status.toUpperCase());
        List<Member> members = memberRepository.findByStatus(memberStatus);
        
        return members.stream()
                .map(MemberDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public MemberDTO getMemberById(Long id) {
        log.info("[Member Service] 회원 단건 조회: id={}", id);
        
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + id));
        
        return MemberDTO.fromEntity(member);
    }
    
    @Transactional
    public MemberDTO updateMember(Long id, MemberUpdateRequestDTO updateDTO) {
        log.info("[Member Service] 회원 정보 수정: id={}", id);
        
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + id));
        
        // 이메일 중복 체크 (본인 제외)
        if (updateDTO.getEmail() != null && !updateDTO.getEmail().equals(member.getEmail())) {
            if (memberRepository.existsByEmail(updateDTO.getEmail())) {
                throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + updateDTO.getEmail());
            }
        }
        
        // 업데이트 수행
        if (updateDTO.getName() != null) {
            member.setName(updateDTO.getName());
        }
        if (updateDTO.getEmail() != null) {
            member.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getPhoneNumber() != null) {
            member.setPhoneNumber(updateDTO.getPhoneNumber());
        }
        if (updateDTO.getRole() != null) {
            member.setRole(Member.Role.valueOf(updateDTO.getRole().toUpperCase()));
        }
        if (updateDTO.getStatus() != null) {
            member.setStatus(Member.MemberStatus.valueOf(updateDTO.getStatus().toUpperCase()));
        }
        if (updateDTO.getDepartment() != null) {
            member.setDepartment(updateDTO.getDepartment());
        }
        if (updateDTO.getPosition() != null) {
            member.setPosition(updateDTO.getPosition());
        }
        if (updateDTO.getFcmToken() != null) {
            member.setFcmToken(updateDTO.getFcmToken());
        }
        
        Member updated = memberRepository.save(member);
        
        log.info("[Member Service] 회원 정보 수정 완료: id={}", id);
        
        return MemberDTO.fromEntity(updated);
    }
    
    @Transactional
    public void deleteMember(Long id) {
        log.info("[Member Service] 회원 삭제: id={}", id);
        
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + id));
        
        memberRepository.delete(member);
        
        log.info("[Member Service] 회원 삭제 완료: id={}", id);
    }
    
    // 알림 전송 헬퍼 메서드들
    private void sendJoinRequestNotificationToAdmins(MemberJoinRequest joinRequest) {
        List<String> adminFcmTokens = getAdminFcmTokens();
        
        for (String adminToken : adminFcmTokens) {
            try {
                FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                        .recipientToken(adminToken)
                        .title("새 회원가입 요청")
                        .message(joinRequest.getName() + "님이 회원가입을 요청했습니다.")
                        .recipientUserId("admin")
                        .recipientUserName("관리자")
                        .type("member_join_requested")
                        .relatedEntityId(joinRequest.getId())
                        .relatedEntityType("member_join_request")
                        .data(Map.of(
                                "type", "member_join_requested",
                                "requestId", String.valueOf(joinRequest.getId()),
                                "requestedRole", joinRequest.getRequestedRole().name().toLowerCase(),
                                "requesterName", joinRequest.getName()
                        ))
                        .build();
                
                notificationService.sendAndSaveNotification(request);
            } catch (Exception e) {
                log.error("[Member Service] 관리자 알림 전송 실패: {}", e.getMessage());
            }
        }
    }
    
    private void sendJoinApprovedNotificationToUser(MemberJoinRequest joinRequest) {
        if (joinRequest.getFcmToken() != null) {
            FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                    .recipientToken(joinRequest.getFcmToken())
                    .title("회원가입 승인")
                    .message("회원가입이 승인되었습니다. 서비스를 이용하실 수 있습니다.")
                    .recipientUserId(joinRequest.getUsername())
                    .recipientUserName(joinRequest.getName())
                    .type("member_join_approved")
                    .relatedEntityId(joinRequest.getId())
                    .relatedEntityType("member_join_request")
                    .data(Map.of(
                            "type", "member_join_approved",
                            "requestId", String.valueOf(joinRequest.getId()),
                            "username", joinRequest.getUsername()
                    ))
                    .build();
            
            notificationService.sendAndSaveNotification(request);
        }
    }
    
    private void sendJoinRejectedNotificationToUser(MemberJoinRequest joinRequest) {
        if (joinRequest.getFcmToken() != null) {
            String message = "회원가입이 거부되었습니다.";
            if (joinRequest.getRejectReason() != null) {
                message += " 사유: " + joinRequest.getRejectReason();
            }
            
            FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                    .recipientToken(joinRequest.getFcmToken())
                    .title("회원가입 거부")
                    .message(message)
                    .recipientUserId(joinRequest.getUsername())
                    .recipientUserName(joinRequest.getName())
                    .type("member_join_rejected")
                    .relatedEntityId(joinRequest.getId())
                    .relatedEntityType("member_join_request")
                    .data(Map.of(
                            "type", "member_join_rejected",
                            "requestId", String.valueOf(joinRequest.getId()),
                            "rejectReason", joinRequest.getRejectReason() != null ? joinRequest.getRejectReason() : ""
                    ))
                    .build();
            
            notificationService.sendAndSaveNotification(request);
        }
    }
    
    // TODO: 실제 환경에서는 관리자 목록과 FCM 토큰을 조회해야 함
    private List<String> getAdminFcmTokens() {
        log.debug("[Member Service] 관리자 FCM 토큰 목록 조회");
        return List.of("test-admin-token-1", "test-admin-token-2");
    }
} 