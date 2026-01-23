package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ScheduleDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleLabelDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleLabelRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.ReminderType;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import com.silverithm.vehicleplacementsystem.entity.ScheduleLabel;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleLabelRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleLabelRepository scheduleLabelRepository;
    private final CompanyRepository companyRepository;

    // ==================== Schedule CRUD ====================

    /**
     * 일정 생성
     */
    @Transactional
    public ScheduleDTO createSchedule(Long companyId, String authorId, String authorName,
                                       ScheduleRequestDTO request) {
        log.info("[Schedule Service] 일정 생성: companyId={}, author={}", companyId, authorName);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        ScheduleLabel label = null;
        if (request.getLabelId() != null) {
            label = scheduleLabelRepository.findById(request.getLabelId())
                    .orElse(null);
        }

        Schedule schedule = Schedule.builder()
                .company(company)
                .title(request.getTitle())
                .content(request.getContent())
                .category(parseCategory(request.getCategory()))
                .label(label)
                .location(request.getLocation())
                .startDate(request.getStartDate())
                .startTime(request.getStartTime())
                .endDate(request.getEndDate())
                .endTime(request.getEndTime())
                .isAllDay(request.getIsAllDay())
                .reminder(parseReminder(request.getReminder()))
                .authorId(authorId)
                .authorName(authorName)
                .build();

        Schedule saved = scheduleRepository.save(schedule);
        log.info("[Schedule Service] 일정 저장 완료: id={}", saved.getId());

        return ScheduleDTO.fromEntity(saved);
    }

    /**
     * 일정 수정
     */
    @Transactional
    public ScheduleDTO updateSchedule(Long scheduleId, ScheduleRequestDTO request) {
        log.info("[Schedule Service] 일정 수정: id={}", scheduleId);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("일정을 찾을 수 없습니다: " + scheduleId));

        ScheduleLabel label = null;
        if (request.getLabelId() != null) {
            label = scheduleLabelRepository.findById(request.getLabelId())
                    .orElse(null);
        }

        schedule.update(
                request.getTitle(),
                request.getContent(),
                request.getCategory() != null ? parseCategory(request.getCategory()) : null,
                label,
                request.getLocation(),
                request.getStartDate(),
                request.getStartTime(),
                request.getEndDate(),
                request.getEndTime(),
                request.getIsAllDay(),
                request.getReminder() != null ? parseReminder(request.getReminder()) : null
        );

        Schedule saved = scheduleRepository.save(schedule);
        log.info("[Schedule Service] 일정 수정 완료: id={}", saved.getId());

        return ScheduleDTO.fromEntity(saved);
    }

    /**
     * 일정 삭제
     */
    @Transactional
    public void deleteSchedule(Long scheduleId) {
        log.info("[Schedule Service] 일정 삭제: id={}", scheduleId);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("일정을 찾을 수 없습니다: " + scheduleId));

        scheduleRepository.delete(schedule);
        log.info("[Schedule Service] 일정 삭제 완료: id={}", scheduleId);
    }

    /**
     * 일정 상세 조회
     */
    @Transactional(readOnly = true)
    public ScheduleDTO getSchedule(Long scheduleId) {
        log.info("[Schedule Service] 일정 조회: id={}", scheduleId);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("일정을 찾을 수 없습니다: " + scheduleId));

        return ScheduleDTO.fromEntity(schedule);
    }

    /**
     * 일정 목록 조회 (기간별)
     */
    @Transactional(readOnly = true)
    public List<ScheduleDTO> getSchedules(Long companyId, LocalDate startDate, LocalDate endDate,
                                           String category, Long labelId, String searchQuery) {
        log.info("[Schedule Service] 일정 목록 조회: companyId={}, {} ~ {}", companyId, startDate, endDate);

        ScheduleCategory scheduleCategory = null;
        if (category != null && !category.equals("ALL") && !category.isEmpty()) {
            scheduleCategory = parseCategory(category);
        }

        List<Schedule> schedules;

        if (startDate != null && endDate != null) {
            if (scheduleCategory != null || labelId != null || (searchQuery != null && !searchQuery.isEmpty())) {
                schedules = scheduleRepository.findByFilters(
                        companyId, scheduleCategory, labelId, searchQuery, startDate, endDate);
            } else {
                schedules = scheduleRepository.findByCompanyIdAndDateRange(companyId, startDate, endDate);
            }
        } else {
            schedules = scheduleRepository.findByCompanyIdOrderByStartDateAscStartTimeAsc(companyId);
        }

        return schedules.stream()
                .map(ScheduleDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 통계 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStats(Long companyId) {
        long total = scheduleRepository.countByCompanyId(companyId);
        long meeting = scheduleRepository.countByCompanyIdAndCategory(companyId, ScheduleCategory.MEETING);
        long event = scheduleRepository.countByCompanyIdAndCategory(companyId, ScheduleCategory.EVENT);
        long training = scheduleRepository.countByCompanyIdAndCategory(companyId, ScheduleCategory.TRAINING);
        long other = scheduleRepository.countByCompanyIdAndCategory(companyId, ScheduleCategory.OTHER);

        return Map.of(
                "total", total,
                "meeting", meeting,
                "event", event,
                "training", training,
                "other", other
        );
    }

    // ==================== ScheduleLabel CRUD ====================

    /**
     * 라벨 생성
     */
    @Transactional
    public ScheduleLabelDTO createLabel(Long companyId, ScheduleLabelRequestDTO request) {
        log.info("[Schedule Service] 라벨 생성: companyId={}, name={}", companyId, request.getName());

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        // 중복 이름 체크
        if (scheduleLabelRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new RuntimeException("이미 존재하는 라벨 이름입니다: " + request.getName());
        }

        ScheduleLabel label = ScheduleLabel.builder()
                .company(company)
                .name(request.getName())
                .color(request.getColor())
                .build();

        ScheduleLabel saved = scheduleLabelRepository.save(label);
        log.info("[Schedule Service] 라벨 저장 완료: id={}", saved.getId());

        return ScheduleLabelDTO.fromEntity(saved);
    }

    /**
     * 라벨 수정
     */
    @Transactional
    public ScheduleLabelDTO updateLabel(Long labelId, ScheduleLabelRequestDTO request) {
        log.info("[Schedule Service] 라벨 수정: id={}", labelId);

        ScheduleLabel label = scheduleLabelRepository.findById(labelId)
                .orElseThrow(() -> new RuntimeException("라벨을 찾을 수 없습니다: " + labelId));

        // 중복 이름 체크 (자기 자신 제외)
        if (request.getName() != null &&
            scheduleLabelRepository.existsByCompanyIdAndNameAndIdNot(
                    label.getCompany().getId(), request.getName(), labelId)) {
            throw new RuntimeException("이미 존재하는 라벨 이름입니다: " + request.getName());
        }

        label.update(request.getName(), request.getColor());

        ScheduleLabel saved = scheduleLabelRepository.save(label);
        log.info("[Schedule Service] 라벨 수정 완료: id={}", saved.getId());

        return ScheduleLabelDTO.fromEntity(saved);
    }

    /**
     * 라벨 삭제
     */
    @Transactional
    public void deleteLabel(Long labelId) {
        log.info("[Schedule Service] 라벨 삭제: id={}", labelId);

        ScheduleLabel label = scheduleLabelRepository.findById(labelId)
                .orElseThrow(() -> new RuntimeException("라벨을 찾을 수 없습니다: " + labelId));

        // 사용 중인 일정 확인
        long usageCount = scheduleRepository.countByLabelId(labelId);
        if (usageCount > 0) {
            throw new RuntimeException("이 라벨을 사용 중인 일정이 " + usageCount + "개 있습니다. 먼저 해당 일정의 라벨을 변경해주세요.");
        }

        scheduleLabelRepository.delete(label);
        log.info("[Schedule Service] 라벨 삭제 완료: id={}", labelId);
    }

    /**
     * 라벨 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleLabelDTO> getLabels(Long companyId) {
        log.info("[Schedule Service] 라벨 목록 조회: companyId={}", companyId);

        List<ScheduleLabel> labels = scheduleLabelRepository.findByCompanyIdOrderByNameAsc(companyId);

        return labels.stream()
                .map(ScheduleLabelDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== Helper Methods ====================

    private ScheduleCategory parseCategory(String category) {
        if (category == null) {
            return ScheduleCategory.OTHER;
        }
        try {
            return ScheduleCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[Schedule Service] 알 수 없는 카테고리: {}", category);
            return ScheduleCategory.OTHER;
        }
    }

    private ReminderType parseReminder(String reminder) {
        if (reminder == null) {
            return ReminderType.NONE;
        }
        try {
            return ReminderType.valueOf(reminder.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[Schedule Service] 알 수 없는 알림 타입: {}", reminder);
            return ReminderType.NONE;
        }
    }
}
