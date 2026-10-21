package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 투약의뢰서·투약 기록 요청·응답. 약 한 개의 모양은 MedicationItem {name, dose, route, slots, timing, note, weekdays}. */
public final class LabMedicationDto {

    private LabMedicationDto() {
    }

    /**
     * weekdays는 먹이는 요일(ISO 1=월 … 7=일). 비어 있거나 null이면 매일이다 — 요일 칸이 없던 옛 의뢰서도 그대로 매일로 읽힌다.
     */
    public record MedicationItem(String name, String dose, String route, List<String> slots, String timing,
                                 String note, List<Integer> weekdays) {
        /** 요일 없이 만드는 옛 호출용 — 매일 */
        public MedicationItem(String name, String dose, String route, List<String> slots, String timing, String note) {
            this(name, dose, route, slots, timing, note, null);
        }

        /** 이 날(요일)에 먹이는 약인가 */
        public boolean givenOn(java.time.LocalDate day) {
            return weekdays == null || weekdays.isEmpty() || weekdays.contains(day.getDayOfWeek().getValue());
        }
    }

    public record RequestInput(Long elderlyId, LocalDate requestedOn, String requesterName,
                               String requesterRelation, List<MedicationItem> medicines, LocalDate startDate,
                               LocalDate endDate, Boolean prescriptionChecked, String status, String note,
                               String writerName, Boolean aiAssisted) {
    }

    public record RequestView(Long id, Long elderlyId, String elderlyName, LocalDate requestedOn,
                              String requesterName, String requesterRelation, List<MedicationItem> medicines,
                              LocalDate startDate, LocalDate endDate, boolean prescriptionChecked, String status,
                              String note, String writerName, boolean aiAssisted, LocalDateTime updatedAt) {
    }

    public record DoseInput(Long elderlyId, Long requestId, LocalDate givenOn, String slot, String status,
                            String givenAt, String giverName, String observation) {
    }

    public record DoseView(Long id, Long elderlyId, String elderlyName, Long requestId, LocalDate givenOn,
                           String slot, String status, String givenAt, String giverName, String observation,
                           LocalDateTime updatedAt) {
    }
}
