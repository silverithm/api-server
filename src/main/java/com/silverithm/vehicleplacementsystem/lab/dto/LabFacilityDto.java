package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** 시설 관리 대장 요청·응답 — 시설 정보(기관당 한 줄)와 항목별 실시 기록. 작성자 이름은 서버가 채운다. */
public final class LabFacilityDto {

    private LabFacilityDto() {
    }

    public record ProfileInput(LocalDate approvedOn, Integer grossAreaM2, String sprinkler, String powerKind,
                               String gasKind, Integer lpgCapacityKg, String elevator, String waterTank,
                               String septic, Map<String, String> appliesOverride, String fireManagerName,
                               String note) {
    }

    /** 아직 저장한 적이 없으면 id·updatedAt이 null이고 모든 칸이 기본값(UNKNOWN)이다 */
    public record ProfileView(Long id, LocalDate approvedOn, Integer grossAreaM2, String sprinkler,
                              String powerKind, String gasKind, Integer lpgCapacityKg, String elevator,
                              String waterTank, String septic, Map<String, String> appliesOverride,
                              String fireManagerName, String note, String writerName,
                              LocalDateTime updatedAt) {
    }

    public record LogInput(String itemCode, LocalDate doneOn, String doneBy, String vendorName, String result,
                           String actionNote, LocalDate reportedOn, LocalDate validUntil, String docLocation) {
    }

    public record LogView(Long id, String itemCode, LocalDate doneOn, String doneBy, String vendorName,
                          String result, String actionNote, LocalDate reportedOn, LocalDate validUntil,
                          String docLocation, String writerName, LocalDateTime updatedAt) {
    }
}
