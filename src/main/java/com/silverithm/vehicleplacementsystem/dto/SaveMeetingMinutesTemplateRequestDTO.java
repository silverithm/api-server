package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 회의록 양식 생성/수정 요청 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveMeetingMinutesTemplateRequestDTO {

    @NotBlank(message = "양식 이름은 필수입니다")
    private String name;

    @NotBlank(message = "섹션 구성은 필수입니다")
    private String sectionsJson;

    private String aiInstruction;

    private String formatExample;

    /** true면 이 양식을 회사의 기본 양식으로 지정하고 나머지는 자동으로 해제된다 */
    private Boolean isDefault;

    private Integer sortOrder;
}
