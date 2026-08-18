package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 열람 대상 지정 입력값. 이름은 서버가 지정 시점에 스냅샷으로 채우므로 받지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalViewerEntryDTO {

    @NotNull(message = "열람 대상 유형은 필수입니다")
    private ApprovalViewerType viewerType;

    @NotNull(message = "열람 대상 ID는 필수입니다")
    private Long refId;
}
