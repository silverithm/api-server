package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.FileOwnershipRepository;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("업로드 파일 접근 통제")
class FileAccessGuardTest {

    private static final String REQUESTER = "admin@example.com";
    private static final Long COMPANY_ID = 7L;

    @Mock
    private FileOwnershipRepository fileOwnershipRepository;

    @Mock
    private CallerCompanyResolver callerCompanyResolver;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private RedisUtils redisUtils;

    @InjectMocks
    private FileAccessGuard guard;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        userDetails = User.withUsername(REQUESTER).password("x").authorities("ROLE_ADMIN").build();
    }

    private void givenRequesterInCompany() {
        lenient().when(callerCompanyResolver.resolveCompanyId(REQUESTER)).thenReturn(Optional.of(COMPANY_ID));
        lenient().when(fileStorageService.getFileUrl(anyString()))
                .thenAnswer(inv -> "https://bucket.s3.ap-northeast-2.amazonaws.com/carev/" + inv.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../etc/passwd",
            "approvals/../../secret.pdf",
            "/etc/passwd",
            "approvals//secret.pdf",
            "approvals\\secret.pdf",
            "approvals/%2e%2e/secret.pdf",
            "https://bucket.s3.amazonaws.com/carev/approvals/x.pdf",
            "approvals",
            ""
    })
    @DisplayName("경로 탈출·절대경로·인코딩 우회는 400으로 거부한다")
    void rejectsUnsafePaths(String rawPath) {
        assertThatThrownBy(() -> guard.requireAccessible(userDetails, rawPath))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        // 형식 단계에서 걸러지므로 저장소 조회까지 가지 않는다
        verify(fileOwnershipRepository, never()).existsApprovalAttachment(anyLong(), any());
    }

    @Test
    @DisplayName("타 기관 파일은 403으로 거부한다")
    void rejectsFileOfAnotherCompany() {
        givenRequesterInCompany();
        givenNoOwnership();

        assertThatThrownBy(() -> guard.requireAccessible(userDetails, "approvals/other-company.pdf"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("소속 기관이 참조하는 파일은 통과시킨다")
    void allowsFileOwnedByCompany() {
        givenRequesterInCompany();
        givenNoOwnership();
        when(fileOwnershipRepository.existsApprovalAttachment(eqCompany(), any())).thenReturn(true);

        assertThat(guard.requireAccessible(userDetails, "approvals/mine.pdf"))
                .isEqualTo("approvals/mine.pdf");
    }

    @Test
    @DisplayName("상대 경로와 절대 S3 URL을 함께 대조한다")
    void matchesBothRelativeAndAbsoluteForms() {
        givenRequesterInCompany();
        givenNoOwnership();

        guardDenied("chat/12/file.png");

        verify(fileOwnershipRepository).existsChatFile(eqCompany(), argThatContainsBothForms());
    }

    @Test
    @DisplayName("업로드 직후에는 업로더 본인만 통과한다")
    void allowsUploaderWithinGracePeriod() {
        when(redisUtils.get("file:upload:attachments/fresh.pdf")).thenReturn(REQUESTER);

        assertThat(guard.requireAccessible(userDetails, "attachments/fresh.pdf"))
                .isEqualTo("attachments/fresh.pdf");

        // 유예로 통과했으므로 소속 기관 조회는 일어나지 않는다
        verify(callerCompanyResolver, never()).resolveCompanyId(anyString());
    }

    @Test
    @DisplayName("다른 사람이 올린 파일은 유예로 통과하지 못한다")
    void graceDoesNotApplyToOtherUploader() {
        when(redisUtils.get("file:upload:attachments/fresh.pdf")).thenReturn("someone-else@example.com");
        givenRequesterInCompany();
        givenNoOwnership();

        assertThatThrownBy(() -> guard.requireAccessible(userDetails, "attachments/fresh.pdf"))
                .isInstanceOf(CustomException.class);
    }

    // ─── 헬퍼 ───

    private void givenNoOwnership() {
        lenient().when(fileOwnershipRepository.existsApprovalAttachment(anyLong(), any())).thenReturn(false);
        lenient().when(fileOwnershipRepository.existsTemplateFile(anyLong(), any())).thenReturn(false);
        lenient().when(fileOwnershipRepository.existsChatFile(anyLong(), any())).thenReturn(false);
        lenient().when(fileOwnershipRepository.existsApprovalStepSignature(anyLong(), any())).thenReturn(false);
        lenient().when(fileOwnershipRepository.existsAdminSignature(anyLong(), any())).thenReturn(false);
        lenient().when(fileOwnershipRepository.existsMemberSignature(anyLong(), any())).thenReturn(false);
        lenient().when(fileOwnershipRepository.existsCompanySeal(anyLong(), any())).thenReturn(false);
    }

    private void guardDenied(String path) {
        try {
            guard.requireAccessible(userDetails, path);
        } catch (CustomException ignored) {
            // 이 테스트의 관심사는 대조에 사용된 후보 값이다
        }
    }

    private Long eqCompany() {
        return org.mockito.ArgumentMatchers.eq(COMPANY_ID);
    }

    private Collection<String> argThatContainsBothForms() {
        return org.mockito.ArgumentMatchers.argThat(paths ->
                paths.contains("chat/12/file.png")
                        && paths.stream().anyMatch(p -> p.startsWith("https://")));
    }
}
