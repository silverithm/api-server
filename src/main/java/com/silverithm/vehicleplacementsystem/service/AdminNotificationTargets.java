package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
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
     * 알림을 받을 관리자 한 사람.
     *
     * <p>토큰만으로는 "누구에게 보낸 알림인지"를 남길 수 없다. 예전에는 관리자 알림을 전부
     * {@code recipientUserId="admin"}이라는 리터럴로 저장했는데, 앱은 자기 id로 알림함을
     * 조회하므로 그 알림들이 아무에게도 보이지 않았다(푸시는 갔지만 알림함에는 없음).
     * 게다가 "admin"에는 기관 구분이 없어 여러 기관의 알림이 한 바구니에 쌓였다.
     *
     * <p>{@code userId}는 채팅과 같은 규약을 쓴다 — 가입 계정 관리자는 {@code admin_<id>},
     * ADMIN 역할 직원은 원시 id. 관리자와 직원은 서로 다른 표라 id가 겹치기 때문이다.
     */
    public record AdminRecipient(String userId, String userName, String fcmToken) {}

    /**
     * 알림을 받을 수 있는 기관 관리자들.
     *
     * <p>한 사람이 가입 계정과 직원 계정을 모두 가진 경우 같은 기기로 두 번 가므로 토큰으로 합친다.
     */
    @Transactional(readOnly = true)
    public List<AdminRecipient> recipientsOf(Company company) {
        if (company == null) {
            return List.of();
        }

        Stream<AdminRecipient> appUsers = company.getUsers() == null ? Stream.of()
                : company.getUsers().stream()
                        .filter(user -> user.getFcmToken() != null && !user.getFcmToken().isEmpty())
                        .map(user -> new AdminRecipient(
                                ChatService.toAdminChatUserId(user.getId()),
                                user.getUsername(),
                                user.getFcmToken()));

        Stream<AdminRecipient> adminMembers = memberRepository
                .findNotifiableByCompanyAndRoles(company.getId(), List.of(Member.Role.ADMIN))
                .stream()
                .map(member -> new AdminRecipient(
                        String.valueOf(member.getId()),
                        member.getName(),
                        member.getFcmToken()));

        List<AdminRecipient> recipients = Stream.concat(appUsers, adminMembers)
                .filter(r -> r.fcmToken() != null && !r.fcmToken().isEmpty())
                .collect(Collectors.toMap(AdminRecipient::fcmToken, r -> r, (first, second) -> first,
                        LinkedHashMap::new))
                .values().stream().toList();

        log.debug("[Admin Targets] 기관 {} 관리자 알림 대상 {}건", company.getId(), recipients.size());
        return recipients;
    }
}
