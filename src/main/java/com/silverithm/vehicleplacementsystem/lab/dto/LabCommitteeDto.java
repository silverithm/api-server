package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 운영위원·운영위원회 회의 요청·응답. attendeeNames는 서버가 채우므로 입력에는 없다. */
public final class LabCommitteeDto {

    private LabCommitteeDto() {
    }

    public record MemberInput(String name, String role, LocalDate termStart, LocalDate termEnd, Boolean active,
                              String note) {
    }

    public record MemberView(Long id, String name, String role, LocalDate termStart, LocalDate termEnd,
                             boolean active, String note, LocalDateTime updatedAt) {
    }

    public record MeetingInput(LocalDate heldOn, String kind, List<Long> attendeeIds, String agenda,
                               String minutes, List<String> decisions, LocalDate nextOn, String writerName,
                               Boolean aiAssisted) {
    }

    public record MeetingView(Long id, LocalDate heldOn, String kind, List<Long> attendeeIds,
                              List<String> attendeeNames, String agenda, String minutes, List<String> decisions,
                              LocalDate nextOn, String writerName, boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
