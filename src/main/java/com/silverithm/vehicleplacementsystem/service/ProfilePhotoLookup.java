package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.ChatPersonRef;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사람 식별자 여럿을 받아 프로필 사진을 한 번에 찾아 준다.
 *
 * 목록 화면(공지 읽은 사람, 댓글 등)에서 사람마다 따로 조회하면 사람 수만큼 쿼리가 나간다.
 * 여기서는 직원 한 번, 관리자 한 번으로 끝낸다.
 *
 * 식별자 규칙은 채팅과 같다 — 관리자는 "admin_" 접두사가 붙는다({@link ChatPersonRef}).
 * 직원과 관리자는 서로 다른 표라 번호가 겹칠 수 있어서, 접두사 없이는 구별할 수 없다.
 */
@Component
@RequiredArgsConstructor
public class ProfilePhotoLookup {

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    /** 식별자 → 사진 URL. 사진이 없거나 사람을 못 찾으면 그 키는 빠진다. */
    public Map<String, String> photosOf(Collection<String> userIds) {
        Set<String> ids = userIds.stream().filter(Objects::nonNull).filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<String, ChatPersonRef> refs = ids.stream()
                .collect(Collectors.toMap(id -> id, ChatPersonRef::of, (a, b) -> a));

        Set<Long> memberIds = refs.values().stream().map(ChatPersonRef::memberId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> appUserIds = refs.values().stream().map(ChatPersonRef::appUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> memberPhotos = memberIds.isEmpty() ? Map.of()
                : memberRepository.findAllById(memberIds).stream()
                .filter(m -> m.getProfileImageUrl() != null)
                .collect(Collectors.toMap(Member::getId, Member::getProfileImageUrl, (a, b) -> a));

        Map<Long, String> adminPhotos = appUserIds.isEmpty() ? Map.of()
                : userRepository.findAllById(appUserIds).stream()
                .filter(u -> u.getProfileImageUrl() != null)
                .collect(Collectors.toMap(AppUser::getId, AppUser::getProfileImageUrl, (a, b) -> a));

        Map<String, String> result = new HashMap<>();
        refs.forEach((id, ref) -> {
            String photo = ref.memberId() != null ? memberPhotos.get(ref.memberId())
                    : ref.appUserId() != null ? adminPhotos.get(ref.appUserId())
                    : null;
            if (photo != null) {
                result.put(id, photo);
            }
        });
        return result;
    }
}
