package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequestViewer;
import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * 전자결재 인가 헬퍼.
 * 승인/반려의 실제 인가는 클라이언트가 보내는 processedBy 파라미터가 아니라
 * JWT에서 해석한 호출자 신원으로 판단한다. (MemberService.verifyPermissionManageAccess 패턴)
 */
@Service
@RequiredArgsConstructor
public class ApprovalAccessService {

    public static final String APPROVAL_MANAGE = "APPROVAL_MANAGE";

    /** 조회 쿼리에 널 대신 넘기는, 어떤 PK와도 겹치지 않는 값 */
    public static final Long NO_MATCH = -1L;

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    /**
     * @param type      ADMIN(AppUser) | MEMBER(Member)
     * @param refId     app_user.id 또는 members.id
     * @param name      표시 이름
     * @param companyId 소속 회사 ID (없으면 null)
     * @param legacyId  프론트 호환 문자열 (admin_<id> 또는 memberId)
     */
    public record CallerIdentity(ApprovalStep.ApproverType type, Long refId, String name, Long companyId,
                                 String legacyId) {
    }

    /**
     * JWT username으로 호출자 신원 해석. Member(username) 우선, 없으면 AppUser(email).
     * (Member 로그인은 username, AppUser 로그인은 email이 principal이 되는 기존 관례)
     */
    public CallerIdentity resolveCaller(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }

        String username = userDetails.getUsername();

        Optional<Member> member = memberRepository.findByUsername(username);
        if (member.isPresent()) {
            Member found = member.get();
            Long companyId = found.getCompany() != null ? found.getCompany().getId() : null;
            return new CallerIdentity(ApprovalStep.ApproverType.MEMBER, found.getId(), found.getName(), companyId,
                    String.valueOf(found.getId()));
        }

        Optional<AppUser> appUser = userRepository.findByEmail(username);
        if (appUser.isPresent()) {
            AppUser found = appUser.get();
            Long companyId = found.getCompany() != null ? found.getCompany().getId() : null;
            return new CallerIdentity(ApprovalStep.ApproverType.ADMIN, found.getId(), found.getUsername(), companyId,
                    "admin_" + found.getId());
        }

        return null;
    }

    public boolean isCompanyAdmin(CallerIdentity caller, Long companyId) {
        if (caller == null || companyId == null) {
            return false;
        }

        if (caller.type() == ApprovalStep.ApproverType.ADMIN) {
            return companyId.equals(caller.companyId());
        }

        // Member 중 ADMIN 역할도 관리자 취급
        return memberRepository.findById(caller.refId())
                .map(member -> member.getRole() == Member.Role.ADMIN
                        && member.getCompany() != null
                        && companyId.equals(member.getCompany().getId()))
                .orElse(false);
    }

    /** legacy(결재선 없는) 요청 처리 인가: 같은 회사 관리자 또는 APPROVAL_MANAGE 보유 Member */
    public void requireAdminOrApprovalManage(CallerIdentity caller, Long companyId) {
        if (caller == null) {
            throw new SecurityException("인증 정보가 없습니다");
        }

        if (isCompanyAdmin(caller, companyId)) {
            return;
        }

        if (caller.type() == ApprovalStep.ApproverType.MEMBER) {
            Member member = memberRepository.findById(caller.refId()).orElse(null);
            if (member != null && member.getCompany() != null
                    && companyId.equals(member.getCompany().getId())) {
                Set<String> permissions = member.getPermissions();
                if (permissions != null && permissions.contains(APPROVAL_MANAGE)) {
                    return;
                }
            }
        }

        throw new SecurityException("결재를 처리할 권한이 없습니다");
    }

    /** 결재선 요청 처리 인가: 현재 대기 단계의 지정 결재자 본인만 */
    public void requireIsStepApprover(CallerIdentity caller, ApprovalStep step) {
        if (caller == null) {
            throw new SecurityException("인증 정보가 없습니다");
        }

        if (step == null) {
            throw new IllegalStateException("처리할 결재 단계가 없습니다");
        }

        if (caller.type() != step.getApproverType() || !caller.refId().equals(step.getApproverRefId())) {
            throw new SecurityException(
                    "현재 결재 순번의 결재자만 처리할 수 있습니다. (현재 차례: " + step.getApproverName() + ")");
        }
    }

    /** 결재선 항목으로 지정 가능한지 검증하고 결재자 정보를 반환 */
    public ResolvedApprover resolveApprover(ApprovalStep.ApproverType type, Long approverId, Long companyId) {
        if (type == ApprovalStep.ApproverType.ADMIN) {
            AppUser appUser = userRepository.findById(approverId)
                    .orElseThrow(() -> new IllegalArgumentException("결재자를 찾을 수 없습니다: " + approverId));
            if (appUser.getCompany() == null || !companyId.equals(appUser.getCompany().getId())) {
                throw new IllegalArgumentException("다른 회사의 결재자는 지정할 수 없습니다: " + appUser.getUsername());
            }
            return new ResolvedApprover(type, appUser.getId(), appUser.getUsername(), "admin_" + appUser.getId());
        }

        Member member = memberRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("결재자를 찾을 수 없습니다: " + approverId));
        if (member.getCompany() == null || !companyId.equals(member.getCompany().getId())) {
            throw new IllegalArgumentException("다른 회사의 결재자는 지정할 수 없습니다: " + member.getName());
        }

        // 결재자 후보 API(getApproverCandidates)가 활성 전 직원을 노출하므로 검증도 동일 기준을 따른다
        if (member.getStatus() != Member.MemberStatus.ACTIVE) {
            throw new IllegalArgumentException("재직 중이 아닌 직원은 결재선에 지정할 수 없습니다: " + member.getName());
        }

        return new ResolvedApprover(type, member.getId(), member.getName(), String.valueOf(member.getId()));
    }

    public record ResolvedApprover(ApprovalStep.ApproverType type, Long refId, String name, String legacyId) {
    }

    /**
     * 호출자의 직책 PK. 직책이 없거나 관리자(AppUser)면 -1을 돌려준다.
     * 조회 쿼리에 널을 넘기지 않기 위한 값이므로 어떤 직책과도 매칭되지 않아야 한다.
     */
    public Long resolvePositionId(CallerIdentity caller) {
        if (caller == null || caller.type() != ApprovalStep.ApproverType.MEMBER) {
            return NO_MATCH;
        }

        return memberRepository.findById(caller.refId())
                .map(member -> member.getPositionEntity() != null ? member.getPositionEntity().getId() : NO_MATCH)
                .orElse(NO_MATCH);
    }

    /** 호출자를 열람 대상 지정 단위로 환산 */
    public ApprovalViewerType toViewerType(CallerIdentity caller) {
        return caller != null && caller.type() == ApprovalStep.ApproverType.ADMIN
                ? ApprovalViewerType.ADMIN
                : ApprovalViewerType.MEMBER;
    }

    /**
     * 문서 열람 가능 여부.
     * 관리자, 기안자 본인, 결재선 참여자는 열람 대상 지정과 무관하게 볼 수 있고,
     * 그 밖에는 개인으로 지정됐거나 지정된 직책을 가지고 있어야 한다.
     */
    public boolean canView(CallerIdentity caller, ApprovalRequest request) {
        if (caller == null || request == null) {
            return false;
        }

        Long companyId = request.getCompany() != null ? request.getCompany().getId() : null;
        if (companyId == null || !companyId.equals(caller.companyId())) {
            return false;
        }

        if (isCompanyAdmin(caller, companyId)) {
            return true;
        }

        if (caller.legacyId().equals(request.getRequesterId())) {
            return true;
        }

        boolean isApprover = request.getSteps().stream()
                .anyMatch(step -> step.getApproverType() == caller.type()
                        && caller.refId().equals(step.getApproverRefId()));
        if (isApprover) {
            return true;
        }

        ApprovalViewerType callerViewerType = toViewerType(caller);
        Long positionId = resolvePositionId(caller);

        for (ApprovalRequestViewer viewer : request.getViewers()) {
            if (viewer.getViewerType() == callerViewerType && caller.refId().equals(viewer.getRefId())) {
                return true;
            }
            if (viewer.getViewerType() == ApprovalViewerType.POSITION && positionId.equals(viewer.getRefId())) {
                return true;
            }
        }

        return false;
    }

    public void requireCanView(CallerIdentity caller, ApprovalRequest request) {
        if (!canView(caller, request)) {
            throw new SecurityException("이 문서를 열람할 권한이 없습니다");
        }
    }

    /** 결재자의 등록 서명 경로 조회 (없으면 null) */
    public String findRegisteredSignature(CallerIdentity caller) {
        if (caller == null) {
            return null;
        }

        if (caller.type() == ApprovalStep.ApproverType.ADMIN) {
            return userRepository.findById(caller.refId())
                    .map(AppUser::getSignatureUrl)
                    .orElse(null);
        }

        return memberRepository.findById(caller.refId())
                .map(Member::getSignatureUrl)
                .orElse(null);
    }
}
