package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleParticipant;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleStartReminderScheduler {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final MemberRepository memberRepository;
    private final FCMService fcmService;

    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void notifyTodayScheduleStart() {
        LocalDate today = LocalDate.now();
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
        // 참석자와 담당자는 겹칠 수 있다 — 같은 사람에게 두 번 울리지 않게 모아서 보낸다
        Set<Long> targets = new LinkedHashSet<>();
        for (ScheduleParticipant participant : scheduleParticipantRepository.findByScheduleId(schedule.getId())) {
            targets.add(participant.getMemberId());
        }
        if (schedule.getManagerMemberId() != null) {
            targets.add(schedule.getManagerMemberId());
        }
        if (targets.isEmpty()) {
            return 0;
        }

        String body = schedule.getStartTime() != null
                ? String.format("%s · %s", schedule.getTitle(), schedule.getStartTime())
                : schedule.getTitle();

        int sent = 0;
        for (Member member : memberRepository.findAllById(targets)) {
            String token = member.getFcmToken();
            if (token == null || token.isEmpty()) {
                continue;
            }
            fcmService.sendNotification(token, "오늘 일정이 있습니다", body,
                    ScheduleService.scheduleNotificationData(schedule));
            log.info("[Schedule Start Reminder] 전송: scheduleId={}, member={}",
                    schedule.getId(), PrivacyMask.name(member.getName()));
            sent++;
        }
        return sent;
    }
}
