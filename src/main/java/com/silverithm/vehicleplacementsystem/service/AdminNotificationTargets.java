package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "이 기관의 관리자에게 알림을 보낸다"를 한 곳에서 푼다.
 *
 * <p>기관 관리자는 두 갈래다 — 가입 계정(AppUser)과 ADMIN 역할을 받은 직원(Member).
 * 예전에는 호출하는 서비스마다 각자 토큰을 모았고, 셋 다 AppUser만 보고 있어서
 * 직원 계정으로 쓰는 관리자는 휴무 신청·결재 상신·가입 요청 알림을 받지 못했다.
 * 같은 실수가 다시 나지 않도록 수집 규칙을 여기 하나로 둔다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationTargets {

    private final MemberRepository memberRepository;

    /**
     * 알림을 받을 수 있는 기관 관리자들의 FCM 토큰.
     *
     * <p>한 사람이 가입 계정과 직원 계정을 모두 가진 경우 같은 기기로 두 번 가므로 토큰으로 합친다.
     */
    @Transactional(readOnly = true)
    public List<String> fcmTokensOf(Company company) {
        if (company == null) {
            return List.of();
        }

        Stream<String> appUserTokens = company.getUsers() == null ? Stream.of()
                : company.getUsers().stream()
                        .map(AppUser::getFcmToken)
                        .filter(token -> token != null && !token.isEmpty());

        Stream<String> adminMemberTokens = memberRepository
                .findNotifiableByCompanyAndRoles(company.getId(), List.of(Member.Role.ADMIN))
                .stream()
                .map(Member::getFcmToken);

        List<String> tokens = Stream.concat(appUserTokens, adminMemberTokens).distinct().toList();
        log.debug("[Admin Targets] 기관 {} 관리자 알림 대상 {}건", company.getId(), tokens.size());
        return tokens;
    }
}
