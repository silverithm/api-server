package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 회의록 생성/수정 요청. 작성자는 JWT에서 해석하므로 받지 않는다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMeetingMinutesRequestDTO {

    @NotBlank(message = "주제는 필수입니다")
    private String title;

    private String location;

    @NotNull(message = "회의 일시는 필수입니다")
    private LocalDateTime meetingStartAt;

    private LocalDateTime meetingEndAt;

    /** [{"key","label","content"}] */
    private String sectionsJson;

    private String rawNotes;

    private List<AttendeeEntry> attendees;

    private List<AttachmentEntry> attachments;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttendeeEntry {
        /** ADMIN | MEMBER | EXTERNAL */
        @NotBlank
        private String attendeeType;
        /** EXTERNAL이면 null */
        private Long refId;
        /** EXTERNAL만 사용 — 내부 인원은 서버가 이름을 다시 조회한다 */
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttachmentEntry {
        @NotBlank
        private String fileUrl;
        @NotBlank
        private String fileName;
        private Long fileSize;
    }
}
