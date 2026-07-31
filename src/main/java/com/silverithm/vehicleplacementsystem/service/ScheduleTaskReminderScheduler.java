package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 일정 종료일이 지났는데 아직 완료되지 않은 할 일을 담당자에게 알린다.
 * 매일 오전 9시, 전날 마감된 업무를 대상으로 한 번만 보낸다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleTaskReminderScheduler {

    private final ScheduleTaskRepository scheduleTaskRepository;
    private final MemberRepository memberRepository;
    private final FCMService fcmService;

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void notifyOverdueTasks() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<ScheduleTask> overdue = scheduleTaskRepository.findOverdueByScheduleEndDate(yesterday);

        if (overdue.isEmpty()) {
            log.info("[Task Reminder] {} 마감 미완료 업무 없음", yesterday);
            return;
        }

        log.info("[Task Reminder] {} 마감 미완료 업무 {}건 알림 시작", yesterday, overdue.size());

        int sent = 0;
        for (ScheduleTask task : overdue) {
            try {
                Member member = memberRepository.findById(task.getAssigneeMemberId()).orElse(null);
                if (member == null || member.getFcmToken() == null || member.getFcmToken().isEmpty()) {
                    continue;
                }

                String body = String.format("%s - %s", task.getSchedule().getTitle(), task.getContent());
                fcmService.sendNotification(member.getFcmToken(), "완료하지 않은 업무가 있습니다", body);
                sent++;
            } catch (Exception e) {
                log.error("[Task Reminder] 알림 전송 실패: taskId={}", task.getId(), e);
            }
        }

        log.info("[Task Reminder] 알림 전송 완료: {}/{}", sent, overdue.size());
    }
}
