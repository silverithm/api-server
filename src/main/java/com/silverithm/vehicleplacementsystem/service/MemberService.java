package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.MemberJoinRequest;
import com.silverithm.vehicleplacementsystem.entity.Notification;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberJoinRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;
    private final SlackService slackService;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    
    public List<CompanyListDTO> getAllCompanies() {
        log.info("[Member Service] 노출된 회사 조회");
        
        List<Company> companies = companyRepository.findByExposeTrueWithUsers();
        
        return companies.stream()
                .map(CompanyListDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public MemberJoinRequestResponseDTO submitJoinRequest(MemberJoinRequestDTO requestDTO) {
        log.info("[Member Service] 회원가입 요청: username={}, email={}, companyId={}", 
                requestDTO.getUsername(), requestDTO.getEmail(), requestDTO.getCompanyId());
        
        // 회사 검증
        Company company = companyRepository.findById(requestDTO.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + requestDTO.getCompanyId()));
        
        // 전역 중복 확인
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
        
        // Role enum 변환
        Member.Role role;
        try {
            role = Member.Role.valueOf(requestDTO.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("잘못된 역할입니다: " + requestDTO.getRole());
        }
        
        // 관리자 역할은 직접 요청할 수 없음
        if (role == Member.Role.ADMIN) {
            throw new IllegalArgumentException("관리자 역할은 직접 요청할 수 없습니다");
        }
        
        // 가입 요청 생성
        MemberJoinRequest joinRequest = MemberJoinRequest.builder()
                .username(requestDTO.getUsername())
                .password(passwordEncoder.encode(requestDTO.getPassword())) // 비밀번호 암호화
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
        
        log.info("[Member Service] 회원가입 요청 생성 완료: 회사 {}, ID={}", company.getName(), saved.getId());

        return MemberJoinRequestResponseDTO.fromEntity(saved);
    }
    
    public List<MemberJoinRequestResponseDTO> getAllJoinRequests() {
        log.info("[Member Service] 모든 가입 요청 조회");
        
        List<MemberJoinRequest> requests = memberJoinRequestRepository.findAllByOrderByCreatedAtDesc();
        
        return requests.stream()
                .map(MemberJoinRequestResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    // 회사별 가입 요청 조회 메서드 추가
    public List<MemberJoinRequestResponseDTO> getAllJoinRequestsByCompany(Long companyId) {
        log.info("[Member Service] 회사별 모든 가입 요청 조회: companyId={}", companyId);
        
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));
        
        List<MemberJoinRequest> requests = memberJoinRequestRepository.findByCompanyOrderByCreatedAtDesc(company);
        
        log.info("[Member Service] 회사별 가입 요청 조회 완료: 회사 {}, {}건", company.getName(), requests.size());
        
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
    
    // 회사별 대기중인 가입 요청 조회 메서드 추가
    public List<MemberJoinRequestResponseDTO> getPendingJoinRequestsByCompany(Long companyId) {
        log.info("[Member Service] 회사별 대기중인 가입 요청 조회: companyId={}", companyId);
        
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));
        
        List<MemberJoinRequest> requests = memberJoinRequestRepository.findByCompanyAndStatusOrderByCreatedAtDesc(
                company, MemberJoinRequest.RequestStatus.PENDING);
        
        log.info("[Member Service] 회사별 대기중인 가입 요청 조회 완료: 회사 {}, {}건", company.getName(), requests.size());
        
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
        
        // 최종 중복 체크 (다른 관리자가 동시에 승인했을 수 있음) - 전역 확인
        if (memberRepository.existsByUsername(joinRequest.getUsername())) {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다");
        }
        
        if (memberRepository.existsByEmail(joinRequest.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다");
        }
        
        // 회원 생성
        Member member = Member.builder()
                .username(joinRequest.getUsername())
                .password(joinRequest.getPassword()) // 이미 암호화된 비밀번호
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
        
        // 슬랙 알림 전송
        try {
            String companyName = joinRequest.getCompany() != null ? joinRequest.getCompany().getName() : "미지정";
            slackService.sendMemberApprovalNotification(
                    joinRequest.getEmail(), 
                    joinRequest.getName(), 
                    companyName,
                    joinRequest.getDepartment(),
                    joinRequest.getPosition(),
                    joinRequest.getRequestedRole().name().toLowerCase()
            );
            log.info("[Member Service] 멤버 승인 슬랙 알림 전송 완료: {}", joinRequest.getName());
        } catch (Exception e) {
            log.error("[Member Service] 슬랙 알림 전송 실패: {}", e.getMessage());
        }
        
        // 신청자에게 승인 알림 전송
//        try {
//            sendJoinApprovedNotificationToUser(joinRequest);
//        } catch (Exception e) {
//            log.error("[Member Service] 승인 알림 전송 실패: {}", e.getMessage());
//        }
        
        // 신청자에게 승인 이메일 전송
        try {
            String companyName = joinRequest.getCompany() != null ? joinRequest.getCompany().getName() : "회사";
            emailService.sendJoinApprovalEmail(
                    joinRequest.getEmail(),
                    joinRequest.getName(),
                    companyName
            );
            log.info("[Member Service] 가입 승인 이메일 전송 완료: {}", joinRequest.getEmail());
        } catch (Exception e) {
            log.error("[Member Service] 가입 승인 이메일 전송 실패: {}", e.getMessage());
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
        joinRequest.setRejectReason(processDTO.getRejectReason());
        joinRequest.setProcessedAt(LocalDateTime.now());
        memberJoinRequestRepository.save(joinRequest);
        
        log.info("[Member Service] 가입 거부 완료: requestId={}, 사유={}", requestId, processDTO.getRejectReason());
        
        // 신청자에게 거부 알림 전송
//        try {
//            sendJoinRejectedNotificationToUser(joinRequest);
//        } catch (Exception e) {
//            log.error("[Member Service] 거부 알림 전송 실패: {}", e.getMessage());
//        }
        
        // 신청자에게 거부 이메일 전송
        try {
            String companyName = joinRequest.getCompany() != null ? joinRequest.getCompany().getName() : "회사";
            emailService.sendJoinRejectionEmail(
                    joinRequest.getEmail(),
                    joinRequest.getName(),
                    companyName,
                    processDTO.getRejectReason()
            );
            log.info("[Member Service] 가입 거부 이메일 전송 완료: {}", joinRequest.getEmail());
        } catch (Exception e) {
            log.error("[Member Service] 가입 거부 이메일 전송 실패: {}", e.getMessage());
        }
    }
    
    public List<MemberDTO> getAllMembers() {
        log.info("[Member Service] 모든 회원 조회");
        
        List<Member> members = memberRepository.findAll();
        
        return members.stream()
                .map(MemberDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    // 회사별 회원 조회 메서드 추가
    public List<MemberDTO> getAllMembersByCompany(Long companyId) {
        log.info("[Member Service] 회사별 모든 회원 조회: companyId={}", companyId);
        
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));
        
        List<Member> members = memberRepository.findByCompanyOrderByCreatedAtDesc(company);
        
        log.info("[Member Service] 회사별 회원 조회 완료: 회사 {}, {}명", company.getName(), members.size());
        
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
    
    // 회사별 역할별 회원 조회 메서드 추가
    public List<MemberDTO> getMembersByCompanyAndRole(Long companyId, String role) {
        log.info("[Member Service] 회사별 역할별 회원 조회: companyId={}, role={}", companyId, role);
        
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));
        
        Member.Role memberRole = Member.Role.valueOf(role.toUpperCase());
        List<Member> members = memberRepository.findByCompanyAndRole(company, memberRole);
        
        log.info("[Member Service] 회사별 역할별 회원 조회 완료: 회사 {}, 역할 {}, {}명", company.getName(), role, members.size());
        
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
    
    // 회사별 상태별 회원 조회 메서드 추가
    public List<MemberDTO> getMembersByCompanyAndStatus(Long companyId, String status) {
        log.info("[Member Service] 회사별 상태별 회원 조회: companyId={}, status={}", companyId, status);
        
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));
        
        Member.MemberStatus memberStatus = Member.MemberStatus.valueOf(status.toUpperCase());
        List<Member> members = memberRepository.findByCompanyAndStatus(company, memberStatus);
        
        log.info("[Member Service] 회사별 상태별 회원 조회 완료: 회사 {}, 상태 {}, {}명", company.getName(), status, members.size());
        
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
            if (memberRepository.existsByEmailAndCompanyId(updateDTO.getEmail(), member.getCompany().getId())) {
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
    
    @Transactional
    public MemberSigninResponseDTO signin(MemberSigninDTO signinDTO) {
        log.info("[Member Service] 로그인 요청: username={}", signinDTO.getUsername());
        
        Member member = memberRepository.findByUsername(signinDTO.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        
        // 비밀번호 검증 (실제 환경에서는 암호화된 비밀번호 비교 필요)
        if (!passwordEncoder.matches(signinDTO.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }
        
        // 계정 상태 확인
        if (member.getStatus() != Member.MemberStatus.ACTIVE) {
            String statusMessage = switch (member.getStatus()) {
                case INACTIVE -> "비활성화된 계정입니다";
                case SUSPENDED -> "정지된 계정입니다";
                default -> "사용할 수 없는 계정입니다";
            };
            throw new IllegalArgumentException(statusMessage);
        }
        
        // JWT 토큰 생성
        UserResponseDTO.TokenInfo tokenInfo = jwtTokenProvider.generateToken(member.getUsername(),
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
        
        // 로그인 성공 처리
        member.setLastLoginAt(LocalDateTime.now());
        
        memberRepository.save(member);
        
        log.info("[Member Service] 로그인 성공: {} (ID: {})", signinDTO.getUsername(), member.getId());
        
        return MemberSigninResponseDTO.from(member, tokenInfo);
    }
    
    @Transactional
    public void updateFcmToken(Long memberId, FCMTokenUpdateDTO tokenUpdateDTO) {
        log.info("[Member Service] FCM 토큰 업데이트: memberId={}", memberId);
        
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + memberId));
        
        member.setFcmToken(tokenUpdateDTO.getFcmToken());
        memberRepository.save(member);
        
        log.info("[Member Service] FCM 토큰 업데이트 완료: memberId={}", memberId);
    }
    
    /**
     * 회원탈퇴 처리
     */
    @Transactional
    public void withdrawMember(String username) {
        log.info("[Member Service] 회원탈퇴 요청: username={}", username);
        
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + username));
        
        // 이미 탈퇴한 회원인지 확인
        if (member.getStatus() == Member.MemberStatus.DELETED) {
            throw new IllegalArgumentException("이미 탈퇴한 회원입니다");
        }
        
        // 관리자는 탈퇴할 수 없음 (최소 1명의 관리자 유지)
        if (member.getRole() == Member.Role.ADMIN) {
            long adminCount = memberRepository.countByRoleAndStatus(Member.Role.ADMIN, Member.MemberStatus.ACTIVE);
            if (adminCount <= 1) {
                throw new IllegalArgumentException("관리자는 최소 1명 이상 유지되어야 합니다. 다른 관리자를 지정한 후 탈퇴해주세요.");
            }
        }
        
        // 상태를 DELETED로 변경 (실제 삭제하지 않고 논리적 삭제)
        member.setStatus(Member.MemberStatus.DELETED);
        
        // 개인정보 마스킹 처리 (선택사항)
        member.setEmail(member.getEmail().replaceAll("(.{2}).*@", "$1***@"));
        member.setPhoneNumber(member.getPhoneNumber() != null ? 
                member.getPhoneNumber().replaceAll("(\\d{3})(\\d{4})(\\d{4})", "$1-****-$3") : null);
        member.setFcmToken(null); // FCM 토큰 제거
        
        memberRepository.save(member);
        
        log.info("[Member Service] 회원탈퇴 완료: username={}", username);
        
        // 관리자에게 탈퇴 알림 (옵션)
        try {
            sendMemberWithdrawalNotificationToAdmins(member);
        } catch (Exception e) {
            log.error("[Member Service] 탈퇴 알림 전송 실패: {}", e.getMessage());
        }
    }
    
    /**
     * 관리자에게 회원탈퇴 알림 전송
     */
    private void sendMemberWithdrawalNotificationToAdmins(Member member) {
        List<String> adminFcmTokens = getAdminFcmTokens();
        
        for (String adminToken : adminFcmTokens) {
            try {
                FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                        .recipientToken(adminToken)
                        .title("회원탈퇴 알림")
                        .message(member.getName() + "님이 회원탈퇴했습니다.")
                        .recipientUserId("admin")
                        .recipientUserName("관리자")
                        .type("member_withdrawal")
                        .relatedEntityId(member.getId())
                        .relatedEntityType("member")
                        .data(Map.of(
                                "type", "member_withdrawal",
                                "memberId", String.valueOf(member.getId()),
                                "memberName", member.getName(),
                                "username", member.getUsername()
                        ))
                        .build();
                
                notificationService.sendAndSaveNotification(request);
            } catch (Exception e) {
                log.error("[Member Service] 관리자 탈퇴 알림 전송 실패: {}", e.getMessage());
            }
        }
    }
} 