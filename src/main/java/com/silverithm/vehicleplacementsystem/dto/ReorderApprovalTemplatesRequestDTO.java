package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 양식 관리 화면에서 드래그(또는 위/아래 이동 버튼)로 정한 새 순서.
 * 보이는 순서 그대로 양식 id를 담아 보낸다 — 서버는 그 순서대로 0부터 sortOrder를 다시 매긴다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReorderApprovalTemplatesRequestDTO {

    @NotEmpty(message = "순서를 지정할 양식 목록이 비어 있습니다")
    private List<Long> orderedTemplateIds;
}
