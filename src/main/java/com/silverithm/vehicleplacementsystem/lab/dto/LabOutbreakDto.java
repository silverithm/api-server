package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 감염병 유행·발생 기록 요청·응답. 작성자는 서버가 사람 이름으로 채우므로 입력에는 없다. */
public final class LabOutbreakDto {

    private LabOutbreakDto() {
    }

    public record Input(String kind, String disease, LocalDate startedOn, Integer elderCases, Integer staffCases,
                        LocalDate guardianNotifiedOn, LocalDate healthCenterReportedOn,
                        LocalDate authorityReportedOn, String alertLevel, List<String> actions, LocalDate endedOn,
                        String note) {
    }

    public record View(Long id, String kind, String disease, LocalDate startedOn, int elderCases, int staffCases,
                       LocalDate guardianNotifiedOn, LocalDate healthCenterReportedOn,
                       LocalDate authorityReportedOn, String alertLevel, List<String> actions, LocalDate endedOn,
                       String note, String writerName, LocalDateTime updatedAt) {
    }
}
