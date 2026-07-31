package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JWT principal → 요청자의 소속 기관(company) 해석.
 *
 * <p>계정이 Member(직원)와 AppUser(관리자) 두 테이블로 나뉘어 있어 조회 순서가 정해져 있다.
 * 인가 판단의 기준이 되는 값이므로 이 해석 로직은 한 곳에만 두고 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class CallerCompanyResolver {

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    /**
     * 요청자의 소속 기관 ID. 사용자를 찾지 못했거나 소속 기관이 없으면 비어 있다.
     *
     * @param username JWT principal (Member는 로그인 아이디, AppUser는 이메일)
     */
    @Transactional(readOnly = true)
    public Optional<Long> resolveCompanyId(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        Optional<Member> member = memberRepository.findByUsername(username);
        if (member.isPresent()) {
            return Optional.ofNullable(member.get().getCompany()).map(c -> c.getId());
        }

        Optional<AppUser> appUser = userRepository.findByEmail(username);
        if (appUser.isPresent()) {
            return Optional.ofNullable(appUser.get().getCompany()).map(c -> c.getId());
        }

        return Optional.empty();
    }
}
