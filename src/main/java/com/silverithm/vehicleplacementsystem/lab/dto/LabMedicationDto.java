package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 투약의뢰서·투약 기록 요청·응답. 약 한 개의 모양은 MedicationItem {name, dose, route, slots, timing, note}. */
public final class LabMedicationDto {

    private LabMedicationDto() {
    }

    public record MedicationItem(String name, String dose, String route, List<String> slots, String timing,
                                 String note) {
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
