package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 연간 사업계획서 요청·응답. 분야는 operation(기관운영)·service(서비스 제공)·staff(직원관리) 셋이다. */
public final class LabBusinessPlanDto {

    private LabBusinessPlanDto() {
    }

    /** budget은 원 단위 0 이상 정수. 비워 둔 칸은 null로 간다 */
    public record Item(String name, String goal, String content, String target, Long budget, String schedule) {
    }

    public record Sections(List<Item> operation, List<Item> service, List<Item> staff) {
    }

    public record Input(Integer planYear, Sections sections, Long totalIncome, Long totalExpense,
                        LocalDate writtenOn, String writerName, Boolean aiAssisted) {
    }

    public record View(Long id, int planYear, Sections sections, Long totalIncome, Long totalExpense,
                       LocalDate writtenOn, String writerName, boolean aiAssisted, LocalDateTime updatedAt) {
    }
}
