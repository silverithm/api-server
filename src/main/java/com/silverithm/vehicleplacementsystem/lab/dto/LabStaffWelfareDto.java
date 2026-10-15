package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 직원 복지(포상) 제공대장과 평가 가산금 요청·응답. 직원 이름은 서버가 채우므로 입력에는 없다. */
public final class LabStaffWelfareDto {

    private LabStaffWelfareDto() {
    }

    public record WelfareInput(LocalDate givenOn, Long memberId, String kind, String item, Long amount,
                               Boolean signed, Boolean recipientIsOwner, Long evidenceLedgerId,
                               String evidenceNote, String note) {
    }

    public record WelfareView(Long id, LocalDate givenOn, Long memberId, String memberName, String kind,
                              String item, long amount, boolean signed, boolean recipientIsOwner,
                              Long evidenceLedgerId, String evidenceNote, String note, LocalDateTime updatedAt) {
    }

    public record BonusInput(String kind, LocalDate occurredOn, Long amount, Long memberId, String purpose,
                             Boolean ownerUse, Long evidenceLedgerId, String note) {
    }

    public record BonusView(Long id, String kind, LocalDate occurredOn, long amount, Long memberId,
                            String memberName, String purpose, boolean ownerUse, Long evidenceLedgerId,
                            String note, LocalDateTime updatedAt) {
    }
}
