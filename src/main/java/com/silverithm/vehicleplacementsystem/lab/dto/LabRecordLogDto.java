package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 종류별 기록 요청·응답. elderlyName은 서버가 채우므로 입력에는 없다. 종류 코드는 LabRecordLogService.KINDS 참고. */
public final class LabRecordLogDto {

    private LabRecordLogDto() {
    }

    public record Input(String kind, LocalDate entryDate, Long elderlyId, String authorName, List<String> checks,
                        String body, String action, String extra, Long amount) {
    }

    public record View(Long id, String kind, LocalDate entryDate, Long elderlyId, String elderlyName,
                       String authorName, List<String> checks, String body, String action, String extra,
                       Long amount, LocalDateTime updatedAt) {
    }
}
