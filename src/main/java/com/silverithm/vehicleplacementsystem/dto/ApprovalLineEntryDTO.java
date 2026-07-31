package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 결재선 지정 항목. 리스트 순서가 결재 순서이며 마지막 항목이 최종 결재자가 된다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalLineEntryDTO {

    @NotNull(message = "결재자 유형은 필수입니다")
    private String approverType;   // ADMIN | MEMBER

    @NotNull(message = "결재자 ID는 필수입니다")
    private Long approverId;       // app_user.id 또는 members.id
}
