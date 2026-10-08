package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.dto.ApprovalRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ApprovalStepDTO;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 결재자가 승인하면서 의견을 남긴다 (2026-10-08 버그제보방: "중간관리자가 결재서류를 승인하는 경우에도
 * 코멘트를 적을 수 있게"). 의견은 내가 승인한 단계에 남고, 결재선 응답에 실려 다음 결재자·기안자가 본다.
 */
class ApprovalApproveCommentTest {

    private static final Long REVIEWER_ID = 7L;

    private ApprovalRequestRepository requestRepository;
    private ApprovalAccessService accessService;
    private ApprovalRequestService service;
    private ApprovalRequest request;
    private final UserDetails reviewerLogin = User.withUsername("reviewer").password("x").roles("USER").build();

    @BeforeEach
    void setUp() {
        requestRepository = mock(ApprovalRequestRepository.class);
        accessService = mock(ApprovalAccessService.class);
        service = new ApprovalRequestService(
                requestRepository, mock(ApprovalTemplateRepository.class), mock(CompanyRepository.class),
                mock(FileStorageService.class), mock(NotificationService.class), mock(AdminNotificationTargets.class),
                mock(MemberRepository.class), mock(UserRepository.class), accessService,
                mock(DocumentNumberCounterRepository.class), mock(ResourceScopeGuard.class),
                mock(ApprovalViewerResolver.class), mock(PositionRepository.class));

        Company company = Company.of("햇살요양원", "서울", null);
        ApprovalTemplate template = ApprovalTemplate.builder().id(1L).company(company).name("물품 구매").build();
        request = ApprovalRequest.builder()
                .id(10L).company(company).template(template).title("위생용품 구매")
                .requesterId("9").requesterName("기안자")
                .status(ApprovalStatus.PENDING).hasApprovalLine(true).currentStepOrder(1)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        request.getSteps().add(step(1, ApprovalStep.StepRole.REVIEWER, REVIEWER_ID, "중간관리자"));
        request.getSteps().add(step(2, ApprovalStep.StepRole.FINAL, 1L, "원장"));

        when(requestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accessService.resolveCaller(any())).thenReturn(new ApprovalAccessService.CallerIdentity(
                ApprovalStep.ApproverType.MEMBER, REVIEWER_ID, "중간관리자", null, String.valueOf(REVIEWER_ID)));
    }

    private ApprovalStep step(int order, ApprovalStep.StepRole role, Long refId, String name) {
        return ApprovalStep.builder()
                .approvalRequest(request).stepOrder(order)
                .approverType(ApprovalStep.ApproverType.MEMBER).approverRefId(refId)
                .approverIdLegacy(String.valueOf(refId)).approverName(name)
                .roleLabel(role).status(ApprovalStep.StepStatus.PENDING)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private ApprovalRequestDTO approve(String comment) {
        return service.approveRequest(10L, "7", "중간관리자", reviewerLogin, null, false, comment);
    }

    @Test
    @DisplayName("중간 결재자가 승인하며 남긴 의견이 그 단계에 남고 결재선 응답에 실린다")
    void reviewerCommentIsKeptOnTheirStep() {
        ApprovalRequestDTO dto = approve("  수량 확인했습니다. 원장님 검토 부탁드립니다 ");

        ApprovalStep reviewerStep = request.getSteps().get(0);
        assertThat(reviewerStep.getStatus()).isEqualTo(ApprovalStep.StepStatus.APPROVED);
        assertThat(reviewerStep.getComment()).isEqualTo("수량 확인했습니다. 원장님 검토 부탁드립니다");
        assertThat(dto.getApprovalLine()).extracting(ApprovalStepDTO::getComment)
                .containsExactly("수량 확인했습니다. 원장님 검토 부탁드립니다", null);
        // 다음 단계로 넘어갔을 뿐 문서는 아직 대기
        assertThat(dto.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("의견 없이 승인하던 예전 방식도 그대로 된다")
    void approveWithoutCommentStillWorks() {
        ApprovalRequestDTO dto = service.approveRequest(10L, "7", "중간관리자", reviewerLogin, null, false);

        assertThat(request.getSteps().get(0).getStatus()).isEqualTo(ApprovalStep.StepStatus.APPROVED);
        assertThat(dto.getApprovalLine().get(0).getComment()).isNull();
    }

    @Test
    @DisplayName("공백뿐인 의견은 남기지 않는다")
    void blankCommentIsNotStored() {
        approve("   ");

        assertThat(request.getSteps().get(0).getComment()).isNull();
    }

    @Test
    @DisplayName("1000자를 넘는 의견은 거절하고 승인도 하지 않는다")
    void tooLongCommentIsRejectedBeforeApproving() {
        String tooLong = "가".repeat(ApprovalRequestService.MAX_APPROVER_COMMENT_LENGTH + 1);

        assertThatThrownBy(() -> approve(tooLong)).isInstanceOf(IllegalArgumentException.class);
        assertThat(request.getSteps().get(0).getStatus()).isEqualTo(ApprovalStep.StepStatus.PENDING);
    }
}
