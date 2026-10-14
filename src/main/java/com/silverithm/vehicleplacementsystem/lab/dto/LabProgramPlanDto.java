package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 프로그램 연간계획·월간 계획표·의견수렴 요청·응답. */
public final class LabProgramPlanDto {

    private LabProgramPlanDto() {
    }

    public record PlanInput(Integer planYear, String category, String goal, String content, String frequency,
                            String target, Long budget, LocalDate writtenOn, String writerName, Boolean aiAssisted) {
    }

    public record PlanView(Long id, int planYear, String category, String goal, String content, String frequency,
                           String target, Long budget, LocalDate writtenOn, String writerName, boolean aiAssisted,
                           LocalDateTime updatedAt) {
    }

    /** date는 'YYYY-MM-DD'(그 달 안), startTime은 'HH:MM' 또는 null */
    public record ScheduleItem(String date, String startTime, String category, String title, String instructor) {
    }

    public record ScheduleInput(String scheduleMonth, List<ScheduleItem> items, String notes, LocalDate sharedOn,
                                String sharedMethod, Boolean aiAssisted) {
    }

    public record ScheduleView(Long id, String scheduleMonth, List<ScheduleItem> items, String notes,
                               LocalDate sharedOn, String sharedMethod, boolean aiAssisted,
                               LocalDateTime updatedAt) {
    }

    public record FeedbackInput(String half, LocalDate collectedOn, String method, Integer participants,
                                String summary, String reflection, LocalDate reflectedOn, String writerName,
                                Boolean aiAssisted) {
    }

    public record FeedbackView(Long id, String half, LocalDate collectedOn, String method, int participants,
                               String summary, String reflection, LocalDate reflectedOn, String writerName,
                               boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
