package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.entity.WorkTask;
import com.silverithm.vehicleplacementsystem.entity.WorkTaskAssignee;
import com.silverithm.vehicleplacementsystem.repository.WorkTaskRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업무진행도 매일 알림 — "업무란에 작성되는 것들은 수행완료될 때까지 매일매일 알람"(2026-10-22 원장 요청).
 *
 * <p>업무마다 한 통씩 보내면 업무가 많은 사람은 아침마다 알림이 쏟아져 결국 알림을 끄게 된다.
 * 그래서 사람마다 하루 한 통으로 묶는다 — "진행 중인 업무 3건 · 기한 지남 1건"과 급한 순 세 줄.
 * 일요일은 근무하지 않아 건너뛴다. 블루그린 배포로 두 서버가 함께 떠 있어도 Redis 하루 잠금으로 한 번만 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkTaskReminderScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WorkTaskRepository tasks;
    private final WorkTaskNotifier notifier;
    private final RedisUtils redisUtils;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void remindDaily() {
        LocalDate today = LocalDate.now(SEOUL);
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return;
        }
        if (!redisUtils.tryAcquireDailySchedulerLock("work-task-reminder", 120)) {
            log.info("[WorkTask Reminder] 다른 서버가 이미 보냈습니다");
            return;
        }
        int sent = notifier.sendNow(buildMessages(today));
        log.info("[WorkTask Reminder] {} 알림 {}건", today, sent);
    }

    /** 담당자마다 진행 중인 업무를 모아 한 통씩 */
    @Transactional(readOnly = true)
    public List<WorkTaskNotifier.Message> buildMessages(LocalDate today) {
        Map<String, List<WorkTask>> byPerson = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (WorkTask task : tasks.findAllInProgress()) {
            for (WorkTaskAssignee assignee : task.getAssignees()) {
                byPerson.computeIfAbsent(assignee.getPersonId(), k -> new ArrayList<>()).add(task);
                names.putIfAbsent(assignee.getPersonId(), assignee.getPersonName());
            }
        }
        List<WorkTaskNotifier.Message> messages = new ArrayList<>();
        byPerson.forEach((personId, list) -> {
            WorkTaskReminderText.Text text = WorkTaskReminderText.dailySummary(list, today);
            // 알림을 누르면 가장 급한 업무로 — 목록 맨 위와 같은 업무다
            Long topTaskId = list.stream().sorted(WorkTaskReminderText.URGENCY).findFirst().map(WorkTask::getId)
                    .orElse(null);
            messages.add(new WorkTaskNotifier.Message(personId, names.get(personId), text.title(), text.body(),
                    topTaskId));
        });
        return messages;
    }
}
