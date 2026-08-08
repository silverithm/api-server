package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 결재선에 지정 가능한 결재자 후보 (회사 관리자 AppUser + 결재 권한 보유 Member).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApproverCandidateDTO {

    private String approverType;   // ADMIN | MEMBER
    private Long approverId;       // app_user.id 또는 members.id
    private String name;
    private String position;       // 역할/직책 (표시용, 없으면 null)
    private String profileImageUrl; // 프로필 사진 (없으면 null — 화면은 이니셜로 대체한다)
}
