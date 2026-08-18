package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 열람 대상 한 줄 — 직책(POSITION) 또는 개인(MEMBER/ADMIN).
 * 응답에는 이름 스냅샷이 함께 실려 프론트가 별도 조회 없이 표시할 수 있다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalViewerDTO {

    private ApprovalViewerType viewerType;
    private Long refId;
    private String viewerName;
}
