package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.VacationEvent;
import com.silverithm.vehicleplacementsystem.repository.VacationEventRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 근무조정 중요 행사 — 관리자가 등록하고 직원이 휴무 신청 시 피하도록 안내한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class VacationEventService {

    private final VacationEventRepository repository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEvents(Long companyId, LocalDate start, LocalDate end) {
        return repository.findOverlapping(companyId, start, end).stream()
                .map(VacationEventService::toMap)
                .toList();
    }

    @Transactional
    public Long createEvent(Long companyId, String title, String description,
                            LocalDate startDate, LocalDate endDate, boolean warnOnRequest, String createdBy) {
        validate(title, startDate, endDate);
        VacationEvent event = VacationEvent.builder()
                .companyId(companyId)
                .title(title.trim())
                .description(description != null && !description.isBlank() ? description.trim() : null)
                .startDate(startDate)
                .endDate(endDate)
                .warnOnRequest(warnOnRequest)
                .createdBy(createdBy)
                .build();
        return repository.save(event).getId();
    }

    @Transactional
    public void updateEvent(Long eventId, Long companyId, String title, String description,
                            LocalDate startDate, LocalDate endDate, boolean warnOnRequest) {
        validate(title, startDate, endDate);
        VacationEvent event = requireOwn(eventId, companyId);
        event.setTitle(title.trim());
        event.setDescription(description != null && !description.isBlank() ? description.trim() : null);
        event.setStartDate(startDate);
        event.setEndDate(endDate);
        event.setWarnOnRequest(warnOnRequest);
    }

    @Transactional
    public void deleteEvent(Long eventId, Long companyId) {
        repository.delete(requireOwn(eventId, companyId));
    }

    private VacationEvent requireOwn(Long eventId, Long companyId) {
        VacationEvent event = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("행사를 찾을 수 없습니다"));
        // 다른 기관의 행사를 건드리지 못하게 한다
        if (!event.getCompanyId().equals(companyId)) {
            throw new IllegalStateException("다른 기관의 행사는 수정할 수 없습니다");
        }
        return event;
    }

    private void validate(String title, LocalDate startDate, LocalDate endDate) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("행사명을 입력해주세요");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("행사 기간을 입력해주세요");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다");
        }
    }

    private static Map<String, Object> toMap(VacationEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("title", event.getTitle());
        map.put("description", event.getDescription());
        map.put("startDate", event.getStartDate().toString());
        map.put("endDate", event.getEndDate().toString());
        map.put("warnOnRequest", event.isWarnOnRequest());
        return map;
    }
}
