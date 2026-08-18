package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 열람 대상으로 지정할 수 있는 후보.
 *
 * <p>직책은 그 직책을 가진 재직 인원수와 함께 준다 — "사회복지사(5명)"처럼 몇 명이 보게 되는지
 * 확인하고 체크할 수 있어야 하기 때문이다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalViewerCandidatesDTO {

    private List<PositionCandidate> positions;
    private List<ApproverCandidateDTO> people;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PositionCandidate {
        private Long id;
        private String name;
        private String description;
        private long memberCount;   // 이 직책을 가진 재직 직원 수
    }
}
