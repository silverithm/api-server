package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 회의록 양식 — 섹션 구성 + AI 자동 정리 지시를 함께 담는다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinutesTemplateDTO {

    /** null이면 저장된 적 없는 애플리케이션 기본 양식(폴백)이다 */
    private Long id;
    private String name;
    /** [{"key","label"}] JSON 문자열 그대로 — 프론트가 파싱한다(기존 계약 유지) */
    private String sectionsJson;
    private String aiInstruction;
    private String formatExample;
    private boolean isDefault;
    private int sortOrder;

    public static MeetingMinutesTemplateDTO of(MeetingMinutesTemplate template) {
        return MeetingMinutesTemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .sectionsJson(template.getSections())
                .aiInstruction(template.getAiInstruction())
                .formatExample(template.getFormatExample())
                .isDefault(template.isDefault())
                .sortOrder(template.getSortOrder())
                .build();
    }
}
