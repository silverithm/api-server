package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 주간 식단 요청·응답. days는 월~토 6일 배열이다. */
public final class LabMealPlanDto {

    private LabMealPlanDto() {
    }

    public record Lunch(String rice, String soup, String main, String side1, String side2, String kimchi) {
    }

    /** date는 보내면 weekStart + 요일 순서와 같아야 하고, 비우면 서버가 채운다. soft는 다진식·죽식 대체 메모 */
    public record Day(String date, Lunch lunch, String amSnack, String pmSnack, String soft) {
    }

    public record Input(LocalDate weekStart, List<Day> days, String notes, String writerName, Boolean aiAssisted) {
    }

    public record View(Long id, LocalDate weekStart, List<Day> days, String notes, String writerName,
                       boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
