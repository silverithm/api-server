package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.MeetingMinutes;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesAttachment;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesAttendee;
import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesAudioChunk;
import com.silverithm.vehicleplacementsystem.service.ApprovalAccessService.CallerIdentity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinutesDTO {

    private Long id;
    private String title;
    private String location;
    private String authorType;
    private Long authorRefId;
    private String authorName;
    private LocalDateTime meetingStartAt;
    private LocalDateTime meetingEndAt;
    private String status;
    private String sectionsJson;
    private String rawNotes;
    private String transcript;
    private Long approvalRequestId;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private int signedCount;
    private int attendeeCount;
    /** 호출자 본인의 참석자 행 id — 직원 화면에서 "내가 서명해야 하는지" 판단용. 참석자가 아니면 null */
    private Long myAttendeeId;
    /** 호출자 본인의 서명 시각 — null이면 아직 미서명(참석자가 아니어도 null) */
    private LocalDateTime mySignedAt;
    private List<AttendeeDTO> attendees;
    private List<AudioChunkDTO> audioChunks;
    private List<AttachmentDTO> attachments;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttendeeDTO {
        private Long id;
        private String attendeeType;
        private Long refId;
        private String attendeeName;
        /** 절대 URL — 화면에서 <img>로 바로 그린다 */
        private String signatureUrl;
        private LocalDateTime signedAt;
        private LocalDateTime notifiedAt;
        private LocalDateTime remindedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AudioChunkDTO {
        private Integer seq;
        /** 저장 경로(상대) — 다운로드는 /files/download 프록시를 탄다 */
        private String filePath;
        private Integer durationSec;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttachmentDTO {
        private Long id;
        private String fileUrl;
        private String fileName;
        private Long fileSize;
    }

    /** 목록용 — 참석자 명단·본문 없이 요약만 (호출자 본인의 서명 여부만 포함) */
    public static MeetingMinutesDTO summaryOf(MeetingMinutes minutes, CallerIdentity caller) {
        return base(minutes, caller).build();
    }

    /** 상세용 — toAbsoluteUrl은 상대 경로를 S3 절대 URL로 바꾼다 (서명 이미지 표시용) */
    public static MeetingMinutesDTO detailOf(MeetingMinutes minutes, CallerIdentity caller,
                                             Function<String, String> toAbsoluteUrl) {
        return base(minutes, caller)
                .sectionsJson(minutes.getSectionsJson())
                .rawNotes(minutes.getRawNotes())
                .transcript(minutes.getTranscript())
                .attendees(minutes.getAttendees().stream()
                        .map(attendee -> AttendeeDTO.builder()
                                .id(attendee.getId())
                                .attendeeType(attendee.getAttendeeType().name())
                                .refId(attendee.getRefId())
                                .attendeeName(attendee.getAttendeeName())
                                .signatureUrl(attendee.getSignatureUrl() != null
                                        ? toAbsoluteUrl.apply(attendee.getSignatureUrl())
                                        : null)
                                .signedAt(attendee.getSignedAt())
                                .notifiedAt(attendee.getNotifiedAt())
                                .remindedAt(attendee.getRemindedAt())
                                .build())
                        .toList())
                .audioChunks(minutes.getAudioChunks().stream()
                        .map(chunk -> AudioChunkDTO.builder()
                                .seq(chunk.getSeq())
                                .filePath(chunk.getFileUrl())
                                .durationSec(chunk.getDurationSec())
                                .build())
                        .toList())
                .attachments(minutes.getAttachments().stream()
                        .map(attachment -> AttachmentDTO.builder()
                                .id(attachment.getId())
                                .fileUrl(attachment.getFileUrl())
                                .fileName(attachment.getFileName())
                                .fileSize(attachment.getFileSize())
                                .build())
                        .toList())
                .build();
    }

    private static MeetingMinutesDTOBuilder base(MeetingMinutes minutes, CallerIdentity caller) {
        List<MeetingMinutesAttendee> attendees = minutes.getAttendees();
        MeetingMinutesAttendee mine = caller == null ? null : attendees.stream()
                .filter(attendee -> attendee.getRefId() != null
                        && attendee.getAttendeeType().name().equals(caller.type().name())
                        && attendee.getRefId().equals(caller.refId()))
                .findFirst()
                .orElse(null);
        return MeetingMinutesDTO.builder()
                .id(minutes.getId())
                .title(minutes.getTitle())
                .location(minutes.getLocation())
                .authorType(minutes.getAuthorType().name())
                .authorRefId(minutes.getAuthorRefId())
                .authorName(minutes.getAuthorName())
                .meetingStartAt(minutes.getMeetingStartAt())
                .meetingEndAt(minutes.getMeetingEndAt())
                .status(minutes.getStatus().name())
                .approvalRequestId(minutes.getApprovalRequest() != null ? minutes.getApprovalRequest().getId() : null)
                .completedAt(minutes.getCompletedAt())
                .createdAt(minutes.getCreatedAt())
                .signedCount((int) attendees.stream().filter(MeetingMinutesAttendee::isSigned).count())
                .attendeeCount(attendees.size())
                .myAttendeeId(mine != null ? mine.getId() : null)
                .mySignedAt(mine != null ? mine.getSignedAt() : null);
    }
}
