package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleTaskRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 일정 종료일이 지났는데 아직 완료되지 않은 할 일을 담당자에게 알린다.
 * 매일 오전 9시, 전날 마감된 업무를 대상으로 한 번만 보낸다.
 *
 * 하루 한 번 도는 알림은 앱이 없는 사람도 웹 알림함에서 볼 수 있게 알림함에 남기고(2026-10-22 원장 요청),
 * 지난 날 것은 오늘 것을 보내기 전에 읽음 처리한다. 매시간 도는 '오늘 미수행 일정' 알림은 푸시만 보낸다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleTaskReminderScheduler {

    private final ScheduleTaskRepository scheduleTaskRepository;
    private final ScheduleRepository scheduleRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;
    private final NotificationService notificationService;
    private final RedisUtils redisUtils;

    static final String OVERDUE_ENTITY_TYPE = "schedule_task_reminder";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void notifyOverdueTasks() {
        // 블루그린 이중 기동 대비 — 알림함에 행을 남기므로 두 서버가 함께 돌면 카드가 두 장씩 뜬다
        if (!redisUtils.tryAcquireDailySchedulerLock("schedule-task-overdue-reminder", 120)) {
            log.info("[Task Reminder] 오늘 알림은 다른 인스턴스가 이미 실행함 — 스킵");
            return;
        }

        LocalDate today = LocalDate.now(SEOUL);
        // 미완료 업무가 없는 날에도 어제 카드는 정리한다 — 리마인더는 그날에만 의미가 있다
        try {
            notificationService.retireStaleReminders(OVERDUE_ENTITY_TYPE, today.atStartOfDay());
        } catch (Exception e) {
            log.error("[Task Reminder] 지난 알림 정리 실패 — 오늘 알림은 계속 보냅니다", e);
        }

        LocalDate yesterday = today.minusDays(1);
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
                if (member == null) {
                    continue;
                }

                String body = String.format("%s - %s", task.getSchedule().getTitle(), task.getContent());
                // 토큰이 없으면 알림함에만 남고, 있으면 푸시도 간다.
                // 데이터가 없으면 알림은 떠도 눌렀을 때 앱이 갈 곳을 모른다
                notificationService.sendAndSaveNotification(FCMNotificationRequestDTO.builder()
                        .recipientToken(member.getFcmToken() == null ? "" : member.getFcmToken())
                        .title("완료하지 않은 업무가 있습니다")
                        .message(body)
                        .recipientUserId(String.valueOf(member.getId()))
                        .recipientUserName(member.getName())
                        .type("GENERAL")
                        .relatedEntityId(task.getSchedule().getId())
                        .relatedEntityType(OVERDUE_ENTITY_TYPE)
                        .data(ScheduleService.scheduleNotificationData(task.getSchedule()))
                        .build());
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
     *
     * 알림함에는 남기지 않고 푸시만 보낸다 — 하루 열 번 도는 알림을 웹 카드로 남기면 그 자체가 스팸이라,
     * 같은 일정은 아침 9시 알림(notifyOverdueTasks·일정 시작 알림)이 알림함에서 맡는다.
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
                // 담당자는 members.id일 수도, app_user.id일 수도 있다(managerType) — 종류에 맞는 테이블에서 조회해야
                // id가 우연히 겹치는 엉뚱한 사람에게 알림이 가거나 아예 못 찾는 것을 막는다.
                String fcmToken;
                if (schedule.getManagerType() == Schedule.ManagerType.ADMIN) {
                    AppUser manager = userRepository.findById(schedule.getManagerMemberId()).orElse(null);
                    fcmToken = manager != null ? manager.getFcmToken() : null;
                } else {
                    Member manager = memberRepository.findById(schedule.getManagerMemberId()).orElse(null);
                    fcmToken = manager != null ? manager.getFcmToken() : null;
                }
                if (fcmToken == null || fcmToken.isEmpty()) {
                    continue;
                }

                fcmService.sendNotification(
                        fcmToken,
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
