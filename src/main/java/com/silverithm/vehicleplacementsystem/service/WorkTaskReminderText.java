package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.WorkTask;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 업무진행도 알림 문구와 급한 순서. 목록 순서와 매일 알림의 순서가 같아야 해서 한곳에 둔다.
 */
public final class WorkTaskReminderText {

    /** 알림 한 통에 늘어놓는 업무 수 — 나머지는 "외 n건" */
    static final int LISTED = 3;
    private static final int CONTENT_IN_LIST = 30;
    private static final Map<String, Integer> IMPORTANCE_RANK = Map.of("HIGH", 0, "NORMAL", 1, "LOW", 2);

    private WorkTaskReminderText() {
    }

    /** 중요도(중요 먼저) → 기한(이른 것 먼저, 없는 것 맨 뒤) → 먼저 등록한 것 */
    public static final Comparator<WorkTask> URGENCY = Comparator
            .comparing((WorkTask t) -> IMPORTANCE_RANK.getOrDefault(t.getImportance(), 1))
            .thenComparing(WorkTask::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(WorkTask::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    public record Text(String title, String body) {
    }

    /** 한 사람의 진행 중인 업무 묶음 → 하루 한 통 */
    public static Text dailySummary(List<WorkTask> inProgress, LocalDate today) {
        List<WorkTask> sorted = inProgress.stream().sorted(URGENCY).toList();
        long overdue = sorted.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today)).count();
        String title = "진행 중인 업무 " + sorted.size() + "건" + (overdue > 0 ? " · 기한 지남 " + overdue + "건" : "");

        StringBuilder body = new StringBuilder();
        for (WorkTask t : sorted.subList(0, Math.min(LISTED, sorted.size()))) {
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append("· ").append(shorten(t.getContent())).append(' ').append(t.getProgress()).append('%');
            String due = dueLabel(t.getDueDate(), today);
            if (due != null) {
                body.append(" · ").append(due);
            }
        }
        if (sorted.size() > LISTED) {
            body.append("\n외 ").append(sorted.size() - LISTED).append("건");
        }
        return new Text(title, body.toString());
    }

    /** "기한 지남" / "오늘까지" / "내일까지" / "D-3" / 기한 없음 → null */
    static String dueLabel(LocalDate due, LocalDate today) {
        if (due == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(today, due);
        if (days < 0) {
            return "기한 지남";
        }
        if (days == 0) {
            return "오늘까지";
        }
        if (days == 1) {
            return "내일까지";
        }
        return "D-" + days;
    }

    /** 배정 알림 끝에 붙이는 기한 — " · 기한 10/25" */
    static String dueSuffix(LocalDate due) {
        return due == null ? "" : " · 기한 " + due.getMonthValue() + "/" + due.getDayOfMonth();
    }

    private static String shorten(String content) {
        String c = content == null ? "" : content.strip().replaceAll("\\s+", " ");
        return c.length() <= CONTENT_IN_LIST ? c : c.substring(0, CONTENT_IN_LIST) + "…";
    }
}
