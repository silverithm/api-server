package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.ChatPersonRef;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업무 담당자 명단 — 채팅 식별자("12" / "admin_3")를 기관 안의 실제 사람으로 바꾼다.
 *
 * <p>다른 기관 사람이나 탈퇴한 사람은 담당자가 될 수 없다. 이름은 여기서 찾은 값만 쓴다.
 */
@Component
@RequiredArgsConstructor
public class WorkTaskPeople {

    private static final int NAME_MAX = 50;

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    /** 식별자 → 이름 (요청 순서 유지, 중복 제거). 기관 밖·없는 사람이 섞이면 400 */
    @Transactional(readOnly = true)
    public Map<String, String> namesInCompany(Collection<String> personIds, Long companyId) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String raw : personIds) {
            String id = raw == null ? null : raw.trim();
            if (id == null || id.isEmpty() || names.containsKey(id)) {
                continue;
            }
            String name = findInCompany(id, companyId)
                    .orElseThrow(() -> new CustomException("담당자를 이 기관 직원에서 찾을 수 없습니다", HttpStatus.BAD_REQUEST));
            names.put(id, name);
        }
        return names;
    }

    /** 알림을 보낼 기기 토큰 — 없으면 비어 있다(알림함에는 그래도 남긴다) */
    @Transactional(readOnly = true)
    public Optional<String> fcmTokenOf(String personId) {
        ChatPersonRef ref = ChatPersonRef.of(personId);
        String token = null;
        if (ref.appUserId() != null) {
            token = userRepository.findById(ref.appUserId()).map(AppUser::getFcmToken).orElse(null);
        } else if (ref.memberId() != null) {
            token = memberRepository.findById(ref.memberId()).map(Member::getFcmToken).orElse(null);
        }
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    private Optional<String> findInCompany(String personId, Long companyId) {
        ChatPersonRef ref = ChatPersonRef.of(personId);
        if (ref.appUserId() != null) {
            return userRepository.findById(ref.appUserId())
                    .filter(u -> u.getDeletedAt() == null)
                    .filter(u -> u.getCompany() != null && companyId.equals(u.getCompany().getId()))
                    .map(AppUser::getUsername)
                    .map(WorkTaskPeople::clip);
        }
        if (ref.memberId() != null) {
            return memberRepository.findById(ref.memberId())
                    .filter(m -> m.getStatus() != Member.MemberStatus.DELETED)
                    .filter(m -> m.getCompany() != null && companyId.equals(m.getCompany().getId()))
                    .map(Member::getName)
                    .map(WorkTaskPeople::clip);
        }
        return Optional.empty();
    }

    private static String clip(String name) {
        String n = name == null ? "" : name.strip();
        return n.length() <= NAME_MAX ? n : n.substring(0, NAME_MAX);
    }
}
