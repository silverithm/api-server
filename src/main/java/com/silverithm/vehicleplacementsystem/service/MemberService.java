package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.MemberJoinRequest;
import com.silverithm.vehicleplacementsystem.entity.Notification;
import com.silverithm.vehicleplacementsystem.entity.Position;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.jwt.CarevPrincipal;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberJoinRequestRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.PositionRepository;
import com.silverithm.vehicleplacementsystem.repository.UserDeviceRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.io.IOException;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.userdetails.UserDetails;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private static final Set<String> ALLOWED_PROFILE_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_PROFILE_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB

    private final MemberRepository memberRepository;
    private final MemberJoinRequestRepository memberJoinRequestRepository;
    private final CompanyRepository companyRepository;
    private final NotificationService notificationService;
    private final AdminNotificationTargets adminNotificationTargets;
    private final DeviceTokenService deviceTokenService;
    private final UserDeviceRepository userDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResourceScopeGuard resourceScopeGuard;
    private final SlackService slackService;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final CompanyCodeService companyCodeService;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ChatService chatService;

    /**
     * JWT 인증된 사용자로부터 adminId를 결정한다.
     * 관리자는 AppUser(app_users)일 수도, Member(members)일 수도 있으므로 둘 다 조회.
     */
    public Long resolveAdminId(UserDetails userDetails, Long fallbackAdminId) {
        if (userDetails == null) {
            return fallbackAdminId;
        }
        String username = userDetails.getUsername();

        // 1. Member 테이블에서 조회
        var memberOpt = memberRepository.findByUsername(username);
        if (memberOpt.isPresent()) {
            return memberOpt.get().getId();
        }

        // 2. AppUser 테이블에서 조회 (관리자)
        var appUserOpt = userRepository.findByEmail(username);
        if (appUserOpt.isPresent()) {
            return appUserOpt.get().getId();
        }

        return fallbackAdminId;
    }

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
                PrivacyMask.name(requestDTO.getUsername()), PrivacyMask.email(requestDTO.getEmail()),
                requestDTO.getCompanyId());

        // 회사 검증
        Company company = resolveCompany(requestDTO);
        Position requestedPosition = resolveRequestedPosition(requestDTO, company);


        if (memberRepository.existsByEmail(requestDTO.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + requestDTO.getEmail());
        }


        Member.Role role = resolveRequestedRole(requestDTO, requestedPosition);

        // 관리자 역할은 직접 요청할 수 없음
        if (role == Member.Role.ADMIN) {
            throw new IllegalArgumentException("관리자 역할은 직접 요청할 수 없습니다");
        }

        String positionName = requestedPosition != null ? requestedPosition.getName() : requestDTO.getPosition();

        // 이미 올린 신청이 대기 중이면 새로 받지 않고 그 사실을 알린다.
        // 조용히 덮어쓰면 본인은 신청된 줄 모르고 계속 누르게 되고, 목록에는 같은 사람이 쌓인다.
        List<MemberJoinRequest> pendingDuplicates = memberJoinRequestRepository.findDuplicatesByStatus(
                company, MemberJoinRequest.RequestStatus.PENDING,
                requestDTO.getUsername(), requestDTO.getEmail());

        if (!pendingDuplicates.isEmpty()) {
            MemberJoinRequest existing = pendingDuplicates.get(0);
            log.info("[Member Service] 중복 가입 신청 차단: companyId={}, 기존 신청 id={}",
                    company.getId(), existing.getId());
            throw new IllegalArgumentException(String.format(
                    "이미 %s에 가입을 신청하셨습니다. 관리자 승인을 기다려주세요. (신청일: %s)",
                    company.getName(),
                    existing.getCreatedAt() != null
                            ? existing.getCreatedAt().toLocalDate().toString()
                            : "확인 불가"));
        }

        MemberJoinRequest joinRequest = MemberJoinRequest.builder()
                .username(requestDTO.getUsername())
                .password(passwordEncoder.encode(requestDTO.getPassword())) // 비밀번호 암호화
                .name(requestDTO.getName())
                .email(requestDTO.getEmail())
                .phoneNumber(requestDTO.getPhoneNumber())
                .requestedRole(role)
                .department(requestDTO.getDepartment())
                .position(positionName)
                .positionEntity(requestedPosition)
                .fcmToken(requestDTO.getFcmToken())
                .company(company)
                .status(MemberJoinRequest.RequestStatus.PENDING)
                .build();

        MemberJoinRequest saved = memberJoinRequestRepository.save(joinRequest);

        log.info("[Member Service] 회원가입 요청 생성 완료: 회사 {}, ID={}", company.getName(), saved.getId());

        // 헬퍼는 예전부터 있었지만 어디서도 부르지 않아 실제로는 한 번도 나가지 않았다.
        try {
            sendJoinRequestNotificationToAdmins(saved);
        } catch (Exception e) {
            // 알림 실패가 가입 신청 자체를 막지는 않는다
            log.error("[Member Service] 가입 신청 관리자 알림 실패: {}", e.getMessage());
        }

        return MemberJoinRequestResponseDTO.fromEntity(saved);
    }

    private Company resolveCompany(MemberJoinRequestDTO requestDTO) {
        if (requestDTO.getCompanyId() != null) {
            return companyRepository.findById(requestDTO.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + requestDTO.getCompanyId()));
        }

        String normalizedCompanyCode = companyCodeService.normalize(requestDTO.getCompanyCode());
        if (!normalizedCompanyCode.isEmpty()) {
            return companyRepository.findByCompanyCodeIgnoreCase(normalizedCompanyCode)
                    .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 회사 코드입니다"));
        }

        throw new IllegalArgumentException("회사 코드 또는 회사 선택이 필요합니다");
    }

    private Position resolveRequestedPosition(MemberJoinRequestDTO requestDTO, Company company) {
        if (requestDTO.getPositionId() == null) {
            return null;
        }

        Position position = positionRepository.findById(requestDTO.getPositionId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 역할을 찾을 수 없습니다."));

        if (!position.getCompany().getId().equals(company.getId())) {
            throw new IllegalArgumentException("선택한 역할이 해당 회사에 속하지 않습니다.");
        }

        return position;
    }

    private Member.Role resolveRequestedRole(MemberJoinRequestDTO requestDTO, Position requestedPosition) {
        if (requestedPosition != null && requestedPosition.getMemberRole() != null) {
            return requestedPosition.getMemberRole();
        }

        if (requestDTO.getRole() == null || requestDTO.getRole().isBlank()) {
            throw new IllegalArgumentException("역할 기본 분류가 필요합니다.");
        }

        try {
            return Member.Role.valueOf(requestDTO.getRole().trim().toUpperCase());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("잘못된 역할입니다: " + requestDTO.getRole());
        }
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

        List<MemberJoinRequestResponseDTO> deduped = dedupeByApplicant(requests);

        log.info("[Member Service] 회사별 대기중인 가입 요청 조회 완료: 회사 {}, {}건(원본 {}건)",
                company.getName(), deduped.size(), requests.size());

        return deduped;
    }

    /**
     * 같은 사람이 여러 번 올린 신청은 한 줄로 보여준다.
     *
     * 신청 단계에서 중복을 막고 있지만, 그 확인이 없던 때 쌓인 건이 남아 있어 목록에서도 걸러낸다.
     * 정렬이 최신순이라 먼저 나온 것(가장 최근 내용)을 남긴다.
     */
    private List<MemberJoinRequestResponseDTO> dedupeByApplicant(List<MemberJoinRequest> requests) {
        Set<String> seen = new HashSet<>();
        List<MemberJoinRequestResponseDTO> result = new ArrayList<>();
        for (MemberJoinRequest request : requests) {
            String key = request.getUsername() != null ? "u:" + request.getUsername()
                    : request.getEmail() != null ? "e:" + request.getEmail()
                    : "id:" + request.getId();
            if (seen.add(key)) {
                result.add(MemberJoinRequestResponseDTO.fromEntity(request));
            }
        }
        return result;
    }

    @Transactional
    public void approveJoinRequest(Long requestId, Long adminId) {
        log.info("[Member Service] 가입 요청 승인: requestId={}, adminId={}", requestId, adminId);

        MemberJoinRequest joinRequest = memberJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("해당 가입 요청을 찾을 수 없습니다: " + requestId));
        resourceScopeGuard.requireSameCompany(joinRequest.getCompany());

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
                .positionEntity(joinRequest.getPositionEntity())
                .company(joinRequest.getCompany())
                .build();

        Member savedMember = memberRepository.save(member);

        // 가입 요청 상태 업데이트
        joinRequest.setStatus(MemberJoinRequest.RequestStatus.APPROVED);
        joinRequest.setApprovedBy(adminId);
        joinRequest.setProcessedAt(LocalDateTime.now());
        memberJoinRequestRepository.save(joinRequest);

        // 같은 사람이 낸 다른 신청도 함께 닫는다 — 승인했는데도 대기 목록에 그 사람이 남아 있으면
        // 관리자는 승인이 안 된 줄 알고 다시 누르게 된다.
        closeSiblingRequests(joinRequest, adminId);

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
            log.info("[Member Service] 멤버 승인 슬랙 알림 전송 완료: {}", PrivacyMask.name(joinRequest.getName()));
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
            log.info("[Member Service] 가입 승인 이메일 전송 완료: {}", PrivacyMask.email(joinRequest.getEmail()));
        } catch (Exception e) {
            log.error("[Member Service] 가입 승인 이메일 전송 실패: {}", e.getMessage());
        }
    }

    @Transactional
    /**
     * 방금 승인한 사람이 낸 다른 대기 신청을 함께 닫는다.
     *
     * 같은 사람이 두 번 신청했는데 하나만 승인하면, 이미 회원이 된 사람이 대기 목록에 계속 남아
     * 관리자가 승인이 안 된 줄 알고 다시 누르게 된다(실제 문의가 있었다). 그 사람은 이미 가입됐으니
     * 남은 신청은 처리할 일이 없다.
     *
     * 거절은 이렇게 하지 않는다 — 거절당한 사람은 다시 신청할 수 있어야 한다.
     */
    private void closeSiblingRequests(MemberJoinRequest approved, Long adminId) {
        List<MemberJoinRequest> siblings = memberJoinRequestRepository.findDuplicatesByStatus(
                approved.getCompany(), MemberJoinRequest.RequestStatus.PENDING,
                approved.getUsername(), approved.getEmail());

        for (MemberJoinRequest sibling : siblings) {
            if (sibling.getId().equals(approved.getId())) {
                continue;
            }
            sibling.setStatus(MemberJoinRequest.RequestStatus.APPROVED);
            sibling.setApprovedBy(adminId);
            sibling.setProcessedAt(LocalDateTime.now());
            memberJoinRequestRepository.save(sibling);
            log.info("[Member Service] 중복 가입 신청 함께 처리: requestId={} (승인된 요청 {})",
                    sibling.getId(), approved.getId());
        }
    }

    public void rejectJoinRequest(Long requestId, Long adminId, MemberJoinRequestProcessDTO processDTO) {
        log.info("[Member Service] 가입 요청 거부: requestId={}, adminId={}", requestId, adminId);

        MemberJoinRequest joinRequest = memberJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("해당 가입 요청을 찾을 수 없습니다: " + requestId));
        resourceScopeGuard.requireSameCompany(joinRequest.getCompany());

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
            log.info("[Member Service] 가입 거부 이메일 전송 완료: {}", PrivacyMask.email(joinRequest.getEmail()));
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

    /**
     * 회사 소속 직원(members) + 관리자(app_user) 계정을 함께 조회.
     * 일정 등록 화면처럼 시설장도 담당자 후보로 보여줘야 하는 화면에서만 옵트인으로 쓴다
     * (기본 목록 조회는 기존처럼 직원만 내려줘야 다른 화면이 깨지지 않는다).
     */
    public List<MemberDTO> getAllMembersAndAdminsByCompany(Long companyId) {
        log.info("[Member Service] 회사별 회원+관리자 조회: companyId={}", companyId);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        List<MemberDTO> members = memberRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .map(MemberDTO::fromEntity)
                .collect(Collectors.toList());

        List<MemberDTO> admins = userRepository.findByCompanyAndDeletedAtIsNull(company).stream()
                .map(MemberDTO::fromAppUser)
                .collect(Collectors.toList());

        log.info("[Member Service] 회사별 회원+관리자 조회 완료: 회사 {}, 직원 {}명, 관리자 {}명",
                company.getName(), members.size(), admins.size());

        List<MemberDTO> result = new ArrayList<>(members);
        result.addAll(admins);
        return result;
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
        resourceScopeGuard.requireSameCompany(member.getCompany());

        return MemberDTO.fromEntity(member);
    }

    @Transactional
    public MemberDTO updateMember(Long id, MemberUpdateRequestDTO updateDTO) {
        log.info("[Member Service] 회원 정보 수정: id={}", id);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(member.getCompany());

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
        resourceScopeGuard.requireSameCompany(member.getCompany());

        // 회원 행을 지우기 전에 채팅방에서 먼저 내보낸다 — 나갔다는 시스템 메시지를 남기기 위해서다.
        // (V1.68부터 참가자 행이 FK로 걸려 있어 회원을 지우면 DB가 알아서 함께 지우지만,
        //  그 경로로는 이 안내가 남지 않는다. 순서를 바꾸지 말 것)
        chatService.handleMemberDeleted(String.valueOf(member.getId()), member.getName());

        memberRepository.delete(member);

        log.info("[Member Service] 회원 삭제 완료: id={}", id);
    }

    // 알림 전송 헬퍼 메서드들
    private void sendJoinRequestNotificationToAdmins(MemberJoinRequest joinRequest) {
        List<String> adminFcmTokens = getAdminFcmTokens(joinRequest.getCompany());

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

    /** 회사 관리자(AppUser)들의 FCM 토큰 목록 조회 */
    private List<String> getAdminFcmTokens(Company company) {
        // 수집 규칙은 AdminNotificationTargets 한 곳에만 둔다 (가입 계정 + ADMIN 역할 직원).
        // 예전에는 여기서 AppUser만 봐서 직원 계정 관리자에게는 가입 요청 알림이 가지 않았다.
        return adminNotificationTargets.fcmTokensOf(company);
    }

    @Transactional
    public MemberSigninResponseDTO signin(MemberSigninDTO signinDTO) {
        log.info("[Member Service] 로그인 요청: username={}", PrivacyMask.name(signinDTO.getUsername()));

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
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())),
                CarevPrincipal.TYPE_MEMBER, member.getId());

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
        resourceScopeGuard.requireSameCompany(member.getCompany());

        // 기기 목록에 등록한다 — 실제 발송 대상은 이쪽이라 폰·태블릿을 같이 써도 모두 받는다
        deviceTokenService.register(memberId, null, tokenUpdateDTO.getFcmToken());

        // 사용자 행의 컬럼은 "마지막에 쓴 기기"로 남겨 둔다 (기존 코드가 아직 참조한다)
        member.setFcmToken(tokenUpdateDTO.getFcmToken());
        memberRepository.save(member);

        log.info("[Member Service] FCM 토큰 업데이트 완료: memberId={}", memberId);
    }

    /** 푸시 알림 수신 여부 조회 (값이 없던 기존 회원은 받는 것으로 본다) */
    @Transactional(readOnly = true)
    public boolean isPushEnabled(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + memberId));
        resourceScopeGuard.requireSameCompany(member.getCompany());
        return !Boolean.FALSE.equals(member.getPushEnabled());
    }

    /** 푸시 알림 수신 on/off */
    @Transactional
    public void updatePushEnabled(Long memberId, boolean enabled) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + memberId));
        resourceScopeGuard.requireSameCompany(member.getCompany());

        member.setPushEnabled(enabled);
        memberRepository.save(member);
        log.info("[Member Service] 알림 수신 설정 변경: memberId={}, enabled={}", memberId, enabled);
    }

    /** 로그아웃 시 기기 토큰 폐기 — 로그아웃한 기기로 알림이 가지 않도록 한다 */
    @Transactional
    public void clearFcmToken(Long memberId, String fcmToken) {
        log.info("[Member Service] FCM 토큰 삭제: memberId={}", memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + memberId));
        resourceScopeGuard.requireSameCompany(member.getCompany());

        if (fcmToken != null && !fcmToken.isBlank()) {
            // 로그아웃한 그 기기만 뗀다. 예전에는 컬럼을 통째로 비워서, 폰에서 로그아웃하면
            // 멀쩡히 쓰던 태블릿까지 알림이 멈췄다.
            deviceTokenService.remove(fcmToken);
            if (fcmToken.equals(member.getFcmToken())) {
                member.setFcmToken(null);
                memberRepository.save(member);
            }
            return;
        }

        // 어느 기기인지 모르면 예전처럼 전부 해제한다 (토큰을 보내지 않는 구버전 앱)
        for (var device : userDeviceRepository.findByMemberId(memberId)) {
            deviceTokenService.remove(device.getFcmToken());
        }
        member.setFcmToken(null);
        memberRepository.save(member);
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
        List<String> adminFcmTokens = getAdminFcmTokens(member.getCompany());

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


    @Transactional
    public FindPasswordResponse findPassword(String email) {
        Member member = memberRepository.findActiveMember(email)
                .orElseThrow(() -> new CustomException("해당 이메일로 가입된 사용자가 없습니다.", HttpStatus.NOT_FOUND));

        String temporaryPassword = createTemporaryPassword(member);

        try {
            sendTemporaryPasswordEmail(email, temporaryPassword);
            return new FindPasswordResponse("임시 비밀번호가 이메일로 전송되었습니다.");
        } catch (Exception e) {
            throw new CustomException("이메일 전송에 실패했습니다 이메일을 다시 확인해 주세요", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    private String createTemporaryPassword(Member member) {
        String temporaryPassword = generateRandomPassword(10);

        String encodedPassword = passwordEncoder.encode(temporaryPassword);

        member.updatePassword(encodedPassword);

        return temporaryPassword;
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(chars.length());
            sb.append(chars.charAt(randomIndex));
        }

        return sb.toString();
    }

    private void sendTemporaryPasswordEmail(String email, String temporaryPassword) {
        String subject = "케어브이 임시 비밀번호 발급";
        String content = temporaryPassword;

        emailService.sendEmailAsync(email, subject, content);
    }

    private static final Set<String> VALID_PERMISSIONS = Set.of(
            "NOTICE_MANAGE", "SCHEDULE_MANAGE", "SCHEDULE_DISPATCH",
            "APPROVAL_MANAGE", "APPROVAL_TEMPLATE", "WORK_MANAGE",
            "MEMBER_VIEW", "MEMBER_MANAGE", "SENIOR_MANAGE"
    );

    public List<String> getMemberPermissions(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + memberId));
        resourceScopeGuard.requireSameCompany(member.getCompany());
        Set<String> perms = member.getPermissions();
        return perms != null ? List.copyOf(perms) : List.of();
    }

    /**
     * 권한 수정 요청자가 대상 멤버의 권한을 관리할 수 있는지 검증한다.
     * - ROLE_ADMIN(관리자): 같은 회사의 멤버만 수정 가능
     * - MEMBER_MANAGE 권한을 위임받은 멤버: 같은 회사의 다른 멤버만 수정 가능 (자기 자신 불가)
     */
    public void verifyPermissionManageAccess(UserDetails userDetails, Long targetMemberId) {
        if (userDetails == null) {
            throw new SecurityException("인증 정보가 없습니다");
        }

        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + targetMemberId));
        Long targetCompanyId = target.getCompany() != null ? target.getCompany().getId() : null;

        String username = userDetails.getUsername();
        boolean hasAdminAuthority = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (hasAdminAuthority) {
            Long adminCompanyId = userRepository.findByEmail(username)
                    .map(u -> u.getCompany() != null ? u.getCompany().getId() : null)
                    .orElseGet(() -> memberRepository.findByUsername(username)
                            .map(m -> m.getCompany() != null ? m.getCompany().getId() : null)
                            .orElse(null));
            if (adminCompanyId == null || !adminCompanyId.equals(targetCompanyId)) {
                throw new SecurityException("다른 회사 회원의 권한은 수정할 수 없습니다");
            }
            return;
        }

        Member caller = memberRepository.findByUsername(username)
                .orElseThrow(() -> new SecurityException("권한을 수정할 수 있는 권한이 없습니다"));
        Set<String> callerPermissions = caller.getPermissions();
        if (callerPermissions == null || !callerPermissions.contains("MEMBER_MANAGE")) {
            throw new SecurityException("권한을 수정할 수 있는 권한이 없습니다");
        }
        if (caller.getId().equals(target.getId())) {
            throw new SecurityException("자신의 권한은 수정할 수 없습니다");
        }
        Long callerCompanyId = caller.getCompany() != null ? caller.getCompany().getId() : null;
        if (callerCompanyId == null || !callerCompanyId.equals(targetCompanyId)) {
            throw new SecurityException("다른 회사 회원의 권한은 수정할 수 없습니다");
        }
    }

    @Transactional
    public List<String> updateMemberPermissions(Long memberId, List<String> permissions) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + memberId));
        resourceScopeGuard.requireSameCompany(member.getCompany());

        // 유효하지 않은 권한 검증
        for (String perm : permissions) {
            if (!VALID_PERMISSIONS.contains(perm)) {
                throw new IllegalArgumentException("유효하지 않은 권한입니다: " + perm);
            }
        }

        member.setPermissions(new HashSet<>(permissions));
        memberRepository.save(member);

        log.info("[Member Service] 멤버 권한 수정 완료: memberId={}, permissions={}", memberId, permissions);

        return List.copyOf(member.getPermissions());
    }

    @Transactional
    public void updateMemberRole(String username, String role) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + username));

        Member.Role newRole;
        try {
            newRole = Member.Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 역할입니다: " + role);
        }

        // ADMIN 역할로 자체 ��격 방지
        if (newRole == Member.Role.ADMIN) {
            throw new IllegalArgumentException("관리자 역할은 직접 변경할 수 없습니다");
        }

        member.updateRole(newRole);
    }

    @Transactional
    public void changePassword(String username, PasswordChangeRequest passwordChangeRequest) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + username));

        if (!passwordEncoder.matches(passwordChangeRequest.currentPassword(), member.getPassword())) {
            throw new CustomException("현재 비밀번호가 올바르지 않습니다", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String newEncodedPassword = passwordEncoder.encode(passwordChangeRequest.newPassword());
        member.updatePassword(newEncodedPassword);
    }

    // ==================== 프로필 사진 ====================

    /**
     * 회원 프로필 사진 업로드. 기존 사진은 best-effort 삭제 후 교체한다.
     * 저장은 FileStorageService.storeFile(..., "profiles") 사용, 응답/DTO 노출은 절대 S3 URL로 한다.
     */
    @Transactional
    public String uploadProfileImage(Long memberId, MultipartFile file, UserDetails userDetails) throws IOException {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + memberId));
        verifyProfileImageAccess(userDetails, member);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase()
                : "";
        if (!ALLOWED_PROFILE_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다. (허용: jpg, jpeg, png, webp)");
        }

        String oldRelativePath = toRelativeFileKey(member.getProfileImageUrl());
        String newRelativePath = fileStorageService.storeFile(file, "profiles");

        if (oldRelativePath != null) {
            try {
                fileStorageService.deleteFile(oldRelativePath);
            } catch (Exception e) {
                log.warn("[Member Service] 기존 프로필 사진 삭제 실패(무시): {}", e.getMessage());
            }
        }

        String absoluteUrl = toAbsoluteFileUrl(newRelativePath);
        member.updateProfileImageUrl(absoluteUrl);
        memberRepository.save(member);

        log.info("[Member Service] 프로필 사진 업로드 완료: memberId={}", memberId);
        return absoluteUrl;
    }

    /** 회원 프로필 사진 삭제 */
    @Transactional
    public void deleteProfileImage(Long memberId, UserDetails userDetails) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다: " + memberId));
        verifyProfileImageAccess(userDetails, member);

        String relativePath = toRelativeFileKey(member.getProfileImageUrl());
        if (relativePath != null) {
            try {
                fileStorageService.deleteFile(relativePath);
            } catch (Exception e) {
                log.warn("[Member Service] 프로필 사진 삭제 실패(무시): {}", e.getMessage());
            }
        }

        member.updateProfileImageUrl(null);
        memberRepository.save(member);
        log.info("[Member Service] 프로필 사진 삭제 완료: memberId={}", memberId);
    }

    /**
     * 프로필 사진 업로드/삭제 권한: 본인이거나, 같은 회사 소속 관리자(ROLE_ADMIN).
     * verifyPermissionManageAccess의 회사 귀속 검증 방식을 따른다.
     */
    private void verifyProfileImageAccess(UserDetails userDetails, Member target) {
        if (userDetails == null) {
            throw new SecurityException("인증 정보가 없습니다");
        }

        String username = userDetails.getUsername();
        if (username != null && username.equals(target.getUsername())) {
            return;
        }

        boolean hasAdminAuthority = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (hasAdminAuthority) {
            Long targetCompanyId = target.getCompany() != null ? target.getCompany().getId() : null;
            Long adminCompanyId = userRepository.findByEmail(username)
                    .map(u -> u.getCompany() != null ? u.getCompany().getId() : null)
                    .orElseGet(() -> memberRepository.findByUsername(username)
                            .map(m -> m.getCompany() != null ? m.getCompany().getId() : null)
                            .orElse(null));
            if (adminCompanyId != null && adminCompanyId.equals(targetCompanyId)) {
                return;
            }
        }

        throw new SecurityException("프로필 사진을 수정할 권한이 없습니다");
    }

    /** 저장된 값(상대경로 또는 절대 S3 URL)을 FileStorageService가 요구하는 상대 경로로 변환한다. */
    private String toRelativeFileKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String prefix = fileStorageService.getFileUrl("");
        if (prefix != null && !prefix.isBlank() && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            // 알 수 없는 호스트의 절대 URL — 삭제 대상 경로를 특정할 수 없으므로 스킵
            return null;
        }
        return value;
    }

    /** 상대 경로를 절대 S3 URL로 변환한다 (signatureUrl 패턴과 동일). */
    private String toAbsoluteFileUrl(String path) {
        if (path == null || path.isEmpty() || path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return fileStorageService.getFileUrl(path);
    }
}
