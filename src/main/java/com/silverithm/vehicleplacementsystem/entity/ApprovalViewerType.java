package com.silverithm.vehicleplacementsystem.entity;

/**
 * 전자결재 문서 열람 대상의 지정 단위.
 *
 * <p>POSITION은 그 직책을 가진 재직 직원 전원이 대상이 되고,
 * MEMBER/ADMIN은 지정된 한 사람만 대상이 된다.
 */
public enum ApprovalViewerType {
    POSITION,  // Position (직책)
    MEMBER,    // Member (직원 개인)
    ADMIN      // AppUser (기관 관리자 개인)
}
