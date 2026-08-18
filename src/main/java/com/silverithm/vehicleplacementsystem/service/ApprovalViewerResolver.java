package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Position;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.PositionRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 열람 대상(직책/개인)의 이름 스냅샷을 만들고, 같은 기관 소속인지 검증한다.
 *
 * <p>양식의 기본 열람 대상과 문서의 열람 대상이 같은 규칙을 써야 해서 한곳에 모아둔다.
 * 이름을 저장 시점에 박아두는 이유는 결재선(approver_name)과 같다 — 표시가 조회마다
 * 흔들리지 않고, 이름으로 문서를 검색할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class ApprovalViewerResolver {

    private final PositionRepository positionRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    public String resolveName(ApprovalViewerType type, Long refId, Long companyId) {
        switch (type) {
            case POSITION -> {
                Position position = positionRepository.findById(refId)
                        .orElseThrow(() -> new IllegalArgumentException("직책을 찾을 수 없습니다: " + refId));
                requireSameCompany(position.getCompany() != null ? position.getCompany().getId() : null,
                        companyId, position.getName());
                return position.getName();
            }
            case ADMIN -> {
                AppUser appUser = userRepository.findById(refId)
                        .orElseThrow(() -> new IllegalArgumentException("관리자를 찾을 수 없습니다: " + refId));
                requireSameCompany(appUser.getCompany() != null ? appUser.getCompany().getId() : null,
                        companyId, appUser.getUsername());
                return appUser.getUsername();
            }
            default -> {
                Member member = memberRepository.findById(refId)
                        .orElseThrow(() -> new IllegalArgumentException("직원을 찾을 수 없습니다: " + refId));
                requireSameCompany(member.getCompany() != null ? member.getCompany().getId() : null,
                        companyId, member.getName());
                return member.getName();
            }
        }
    }

    private void requireSameCompany(Long viewerCompanyId, Long companyId, String label) {
        if (companyId == null || !companyId.equals(viewerCompanyId)) {
            throw new IllegalArgumentException("다른 기관은 열람 대상으로 지정할 수 없습니다: " + label);
        }
    }
}
