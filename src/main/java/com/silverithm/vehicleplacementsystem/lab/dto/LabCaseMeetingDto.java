package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 사례관리 회의 요청·응답. 이름 칸은 서버가 채우므로 입력에는 없다. */
public final class LabCaseMeetingDto {

    private LabCaseMeetingDto() {
    }

    public record OpinionInput(Long memberId, String opinion) {
    }

    public record TargetInput(Long elderlyId, String reason, String plan, Long ownerMemberId, LocalDate reflectedOn,
                              String reflectMethod, String reflectNote, LocalDate evaluatedOn, String evaluatorName,
                              Boolean resolved, String nextAction) {
    }

    public record Input(LocalDate heldOn, List<Long> attendeeIds, List<OpinionInput> opinions,
                        List<TargetInput> targets, String minutes, Boolean aiAssisted) {
    }

    public record Attendee(Long memberId, String memberName) {
    }

    public record Opinion(Long memberId, String memberName, String opinion) {
    }

    public record Target(Long elderlyId, String elderlyName, String reason, String plan, Long ownerMemberId,
                         String ownerName, LocalDate reflectedOn, String reflectMethod, String reflectNote,
                         LocalDate evaluatedOn, String evaluatorName, Boolean resolved, String nextAction) {
    }

    public record View(Long id, LocalDate heldOn, List<Attendee> attendees, List<Opinion> opinions,
                       List<Target> targets, String minutes, String writerName, boolean aiAssisted,
                       LocalDateTime updatedAt) {
    }
}
