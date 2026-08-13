package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
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
    private final ScheduleRepository scheduleRepository;
    private final MemberRepository memberRepository;
    private final FCMService fcmService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
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
                // 데이터가 없으면 알림은 떠도 눌렀을 때 앱이 갈 곳을 모른다
                fcmService.sendNotification(member.getFcmToken(), "완료하지 않은 업무가 있습니다", body,
                        ScheduleService.scheduleNotificationData(task.getSchedule()));
                sent++;
            } catch (Exception e) {
                log.error("[Task Reminder] 알림 전송 실패: taskId={}", task.getId(), e);
            }
        }

        log.info("[Task Reminder] 알림 전송 완료: {}/{}", sent, overdue.size());
    }

    /**
     * 오늘 담당 일정을 아직 수행완료로 바꾸지 않은 담당자에게 한 시간 간격으로 알린다.
     *
     * 업무 시간(09~18시) 정각에만 보낸다. 하루 종일 울리면 알림을 꺼버리게 되고,
     * 그러면 정작 필요한 알림도 안 보게 된다.
     */
    @Scheduled(cron = "0 0 9-18 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void notifyIncompleteTodaySchedules() {
        LocalDate today = LocalDate.now();
        List<Schedule> incomplete = scheduleRepository.findTodayIncompleteWithManager(today);

        if (incomplete.isEmpty()) {
            log.info("[Schedule Reminder] {} 미수행 일정 없음", today);
            return;
        }

        log.info("[Schedule Reminder] {} 미수행 일정 {}건 알림 시작", today, incomplete.size());

        int sent = 0;
        for (Schedule schedule : incomplete) {
            try {
                Member manager = memberRepository.findById(schedule.getManagerMemberId()).orElse(null);
                if (manager == null || manager.getFcmToken() == null || manager.getFcmToken().isEmpty()) {
                    continue;
                }

                fcmService.sendNotification(
                        manager.getFcmToken(),
                        "오늘 일정이 아직 완료되지 않았습니다",
                        schedule.getTitle(),
                        ScheduleService.scheduleNotificationData(schedule));
                sent++;
            } catch (Exception e) {
                log.error("[Schedule Reminder] 알림 전송 실패: scheduleId={}", schedule.getId(), e);
            }
        }

        log.info("[Schedule Reminder] 알림 전송 완료: {}/{}", sent, incomplete.size());
    }
}
