package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleParticipant;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 오늘 시작하는 일정을 아침에 한 번 참석자에게 알린다.
 *
 * 일정은 등록하는 순간에도 알리지만, 며칠 전에 잡아둔 일정은 그때 받은 알림을 기억하기
 * 어렵다. 당일 아침 한 번이 실제로 놓치지 않게 해주는 지점이다.
 * '알림 보내기'를 켜 둔 일정만 대상이라, 조용히 둔 일정은 그대로 조용하다.
 *
 * 하루 한 번만 보낸다 — 시작 시각마다 울리게 하면 알림 자체를 꺼버리게 된다.
 *
 * 앱이 없는 사람도 웹 알림함에는 남긴다(2026-10-22 원장 "새 알림은 전부 알림 하나로") — 기기 토큰이 없으면 알림함에만,
 * 있으면 푸시도 함께 간다. 지난 날 아침 알림은 오늘 것을 보내기 전에 읽음 처리해 카드가 쌓이지 않게 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleStartReminderScheduler {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisUtils redisUtils;

    static final String ENTITY_TYPE = "schedule_start_reminder";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void notifyTodayScheduleStart() {
        // 블루그린 이중 기동 대비 — 알림함에 행을 남기므로 두 서버가 함께 돌면 카드가 두 장씩 뜬다
        if (!redisUtils.tryAcquireDailySchedulerLock("schedule-start-reminder", 120)) {
            log.info("[Schedule Start Reminder] 오늘 알림은 다른 인스턴스가 이미 실행함 — 스킵");
            return;
        }

        LocalDate today = LocalDate.now(SEOUL);
        // 일정이 없는 날에도 어제 카드는 정리한다
        retireStale(today);
        List<Schedule> schedules = scheduleRepository.findByStartDateWithNotification(today);

        if (schedules.isEmpty()) {
            log.info("[Schedule Start Reminder] {} 시작 일정 없음", today);
            return;
        }

        log.info("[Schedule Start Reminder] {} 시작 일정 {}건 알림 시작", today, schedules.size());

        int sent = 0;
        for (Schedule schedule : schedules) {
            try {
                sent += notifyOne(schedule);
            } catch (Exception e) {
                log.error("[Schedule Start Reminder] 알림 전송 실패: scheduleId={}", schedule.getId(), e);
            }
        }

        log.info("[Schedule Start Reminder] 알림 전송 완료: {}건", sent);
    }

    private int notifyOne(Schedule schedule) {
        // 참석자와 담당자는 겹칠 수 있다 — 같은 사람에게 두 번 울리지 않게 모아서 보낸다.
        // 담당자는 members.id일 수도, app_user.id일 수도 있다(managerType) — 두 id 공간이
        // 서로 겹칠 수 있으므로 절대 같은 Set에 섞지 않는다.
        Set<Long> memberTargets = new LinkedHashSet<>();
        for (ScheduleParticipant participant : scheduleParticipantRepository.findByScheduleId(schedule.getId())) {
            memberTargets.add(participant.getMemberId());
        }
        Set<Long> adminTargets = new LinkedHashSet<>();
        if (schedule.getManagerMemberId() != null) {
            if (schedule.getManagerType() == Schedule.ManagerType.ADMIN) {
                adminTargets.add(schedule.getManagerMemberId());
            } else {
                memberTargets.add(schedule.getManagerMemberId());
            }
        }
        if (memberTargets.isEmpty() && adminTargets.isEmpty()) {
            return 0;
        }

        String body = schedule.getStartTime() != null
                ? String.format("%s · %s", schedule.getTitle(), schedule.getStartTime())
                : schedule.getTitle();

        int sent = 0;
        for (Member member : memberRepository.findAllById(memberTargets)) {
            // 사람 한 명이 실패해도 나머지에게는 간다
            try {
                send(schedule, body, member.getFcmToken(), String.valueOf(member.getId()), member.getName());
                log.info("[Schedule Start Reminder] 전송: scheduleId={}, member={}",
                        schedule.getId(), PrivacyMask.name(member.getName()));
                sent++;
            } catch (Exception e) {
                log.error("[Schedule Start Reminder] 알림 실패: scheduleId={}, memberId={}",
                        schedule.getId(), member.getId(), e);
            }
        }
        for (AppUser admin : userRepository.findAllById(adminTargets)) {
            try {
                // 관리자는 채팅과 같은 "admin_<id>"로 남겨야 웹 알림함(MyNotificationService)이 읽는다
                send(schedule, body, admin.getFcmToken(), ChatService.toAdminChatUserId(admin.getId()),
                        admin.getUsername());
                log.info("[Schedule Start Reminder] 전송: scheduleId={}, admin={}",
                        schedule.getId(), PrivacyMask.name(admin.getUsername()));
                sent++;
            } catch (Exception e) {
                log.error("[Schedule Start Reminder] 알림 실패: scheduleId={}, adminId={}",
                        schedule.getId(), admin.getId(), e);
            }
        }
        return sent;
    }

    /** 토큰이 없으면 알림함에만 남고(NotificationService가 판단), 있으면 푸시도 간다 */
    private void send(Schedule schedule, String body, String token, String recipientId, String recipientName) {
        notificationService.sendAndSaveNotification(FCMNotificationRequestDTO.builder()
                .recipientToken(token == null ? "" : token)
                .title("오늘 일정이 있습니다")
                .message(body)
                .recipientUserId(recipientId)
                .recipientUserName(recipientName)
                .type("GENERAL")
                .relatedEntityId(schedule.getId())
                .relatedEntityType(ENTITY_TYPE)
                .data(ScheduleService.scheduleNotificationData(schedule))
                .build());
    }

    /** 리마인더는 그날에만 의미가 있다 — 오늘 이전에 만든 안읽은 카드를 읽음 처리한다. 실패해도 오늘 알림은 보낸다 */
    private void retireStale(LocalDate today) {
        try {
            notificationService.retireStaleReminders(ENTITY_TYPE, today.atStartOfDay());
        } catch (Exception e) {
            log.error("[Schedule Start Reminder] 지난 알림 정리 실패 — 오늘 알림은 계속 보냅니다", e);
        }
    }
}
