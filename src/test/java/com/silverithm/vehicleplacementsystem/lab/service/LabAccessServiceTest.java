package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import com.silverithm.vehicleplacementsystem.service.CallerCompanyResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 실험실 접근 판정 — 허용 기관 소속이면서 관리자일 때만 true. */
class LabAccessServiceTest {

    private final CallerCompanyResolver resolver = mock(CallerCompanyResolver.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private LabAccessService service(String companyIds) {
        return new LabAccessService(companyIds, resolver, memberRepository, userRepository);
    }

    private void login(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }

    private void adminAccount(String email, long companyId) {
        when(resolver.resolveCompanyId(email)).thenReturn(Optional.of(companyId));
        when(memberRepository.findByUsername(email)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mock(AppUser.class)));
    }

    // 실험실이 열리지 않은 기관의 관리자는 쓸 수 없다
    @Test
    void nonLabCompanyIsNotEnabled() {
        adminAccount("admin@other.kr", 7L);
        login("admin@other.kr");

        LabAccessService service = service("4");

        assertThat(service.isEnabled()).isFalse();
        assertThatThrownBy(service::requireCompanyId)
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo("실험실은 아직 이 기관에 열려 있지 않습니다");
                });
    }

    // 허용 기관의 관리자 계정(AppUser)은 쓸 수 있고, 기관 ID는 서버가 해석한다
    @Test
    void labCompanyAdminIsEnabled() {
        adminAccount("admin@forest.kr", 4L);
        login("admin@forest.kr");

        LabAccessService service = service("4");

        assertThat(service.isEnabled()).isTrue();
        assertThat(service.requireCompanyId()).isEqualTo(4L);
        assertThat(service.requireCaller().username()).isEqualTo("admin@forest.kr");
    }

    // 허용 기관의 직원은 관리자 역할이 아니면 쓸 수 없고, 관리자 역할 직원은 쓸 수 있다
    @Test
    void labCompanyNonAdminIsNotEnabled() {
        Member caregiver = mock(Member.class);
        when(caregiver.getRole()).thenReturn(Member.Role.CAREGIVER);
        when(resolver.resolveCompanyId("carer1")).thenReturn(Optional.of(4L));
        when(memberRepository.findByUsername("carer1")).thenReturn(Optional.of(caregiver));
        login("carer1");

        assertThat(service("4").isEnabled()).isFalse();

        Member adminMember = mock(Member.class);
        when(adminMember.getRole()).thenReturn(Member.Role.ADMIN);
        when(resolver.resolveCompanyId("boss1")).thenReturn(Optional.of(4L));
        when(memberRepository.findByUsername("boss1")).thenReturn(Optional.of(adminMember));
        login("boss1");

        assertThat(service("4").isEnabled()).isTrue();
    }

    // 로그인하지 않았으면 쓸 수 없다
    @Test
    void anonymousIsNotEnabled() {
        assertThat(service("4").isEnabled()).isFalse();
    }

    // 기관 목록은 쉼표로 여러 개를 적을 수 있고, 잘못 적힌 조각은 건너뛴다
    @Test
    void companyListParsing() {
        adminAccount("admin@b.kr", 9L);
        login("admin@b.kr");

        assertThat(service("4, 9").isEnabled()).isTrue();
        assertThat(service("4,abc,,").isEnabled()).isFalse();
        assertThat(service("").isEnabled()).isFalse();
    }
}
