package com.silverithm.vehicleplacementsystem.dto;

import lombok.*;

/**
 * 회원관리 화면에 직원과 나란히 놓기 위한 관리자 계정 요약.
 *
 * 직원(MemberDTO)이 갖는 상태·권한·최근 로그인 같은 값은 관리자에게 개념이 없어 담지 않는다.
 * 한 표에 섞을 때 필요한 최소한(누구인지, 어떤 직책인지, 사진)만 준다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSummaryDTO {

    private Long id;
    private String name;
    private String email;
    /** 직책을 정하지 않았으면 '관리자' */
    private String position;
    private Long positionId;
    private String profileImageUrl;
}
