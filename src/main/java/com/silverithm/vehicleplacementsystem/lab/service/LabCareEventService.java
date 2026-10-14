package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareEventDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCareEvent;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCareEventRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태변화·사고·응급 기록 — 등록·수정·삭제와 월별/어르신별/열린 것 조회(최신순).
 *
 * <p>일어난 시각은 미래일 수 없다(시계 오차를 감안해 10분까지만 허용). 보호자 연락은 사고 뒤여야 하고,
 * 연락 시각과 방법은 짝으로만 받는다. 닫을 때는 후속 조치가 있어야 하고, 낙상·부상·응급·입원·투약이거나
 * 중증도가 높은 사고는 보호자 연락 기록도 있어야 한다. 닫는 날은 비우면 서울 오늘로 채우고, 다시 열면 비운다.
 */
@Service
public class LabCareEventService {

    private static final int LONG_TEXT = 2000;
    private static final int RESPONSE_TEXT = 1000;
    private static final int PLACE = 100;
    private static final int NAME = 50;
    private static final long FUTURE_TOLERANCE_MINUTES = 10;
    private static final Set<String> KINDS = Set.of("CONDITION_CHANGE", "FALL", "INJURY", "EMERGENCY",
            "HOSPITAL", "MEDICATION", "BEHAVIOR", "OTHER");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MODERATE", "HIGH", "CRITICAL");
    private static final Set<String> METHODS = Set.of("PHONE", "SMS", "VISIT", "OTHER");
    private static final Set<String> STATUSES = Set.of(LabCareEvent.OPEN, LabCareEvent.CLOSED);
    /** 종류만으로 보호자 연락이 필요한 사고 */
    private static final Set<String> GUARDIAN_KINDS = Set.of("FALL", "INJURY", "EMERGENCY", "HOSPITAL", "MEDICATION");
    private static final Set<String> GUARDIAN_SEVERITIES = Set.of("HIGH", "CRITICAL");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabCareEventRepository events;

    public LabCareEventService(LabAccessService access, LabElderDirectory elders, LabCareEventRepository events) {
        this.access = access;
        this.elders = elders;
        this.events = events;
    }

    /**
     * month / elderlyId / status 중 하나 이상 — 여러 개면 모두 만족하는 것만. 최신순(발생 시각 내림차순).
     * 가장 좁히는 조건으로 쿼리하고 나머지는 메모리에서 거른다(기관 한 곳의 사고 기록은 많지 않다).
     */
    @Transactional(readOnly = true)
    public List<LabCareEventDto.View> list(String month, Long elderlyId, String status) {
        Long companyId = access.requireCompanyId();
        if (month == null && elderlyId == null && status == null) {
            throw LabValidation.bad("월(YYYY-MM), 어르신 또는 상태를 지정해 주세요");
        }
        String wantedStatus = status == null ? null : LabValidation.oneOf(status, STATUSES, "상태");
        YearMonth ym = month == null ? null : LabValidation.yearMonth(month, "월");
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
        }

        List<LabCareEvent> rows;
        if (ym != null) {
            rows = events.findByCompanyIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
                    companyId, ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay());
        } else if (elderlyId != null) {
            rows = events.findByCompanyIdAndElderlyIdOrderByOccurredAtDescIdDesc(companyId, elderlyId);
        } else {
            rows = events.findByCompanyIdAndStatusOrderByOccurredAtDescIdDesc(companyId, wantedStatus);
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream()
                .filter(e -> elderlyId == null || e.getElderlyId().equals(elderlyId))
                .filter(e -> wantedStatus == null || e.getStatus().equals(wantedStatus))
                .map(e -> toView(e, names.getOrDefault(e.getElderlyId(), "")))
                .toList();
    }

    @Transactional
    public LabCareEventDto.View create(LabCareEventDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabCareEvent event = new LabCareEvent(companyId);
        apply(event, input, companyId);
        LabCareEvent saved = events.saveAndFlush(event);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public LabCareEventDto.View update(Long id, LabCareEventDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabCareEvent event = events.findByIdAndCompanyId(id, companyId).orElseThrow(LabCareEventService::notFound);
        apply(event, input, companyId);
        LabCareEvent saved = events.saveAndFlush(event);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        events.delete(events.findByIdAndCompanyId(id, companyId).orElseThrow(LabCareEventService::notFound));
    }

    private void apply(LabCareEvent event, LabCareEventDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("사고 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        LocalDateTime now = LabValidation.seoulNow();
        LocalDateTime occurredAt = LabValidation.dateTimeOrNull(input.occurredAt(), "일어난 시각");
        if (occurredAt == null) {
            throw LabValidation.bad("일어난 시각을 입력해 주세요");
        }
        if (occurredAt.isAfter(now.plusMinutes(FUTURE_TOLERANCE_MINUTES))) {
            throw LabValidation.bad("일어난 시각이 지금보다 뒤일 수 없습니다");
        }
        String kind = LabValidation.oneOf(input.kind(), KINDS, "종류");
        String severity = LabValidation.oneOf(input.severity(), SEVERITIES, "중증도");
        String place = LabValidation.text(input.place(), PLACE, "장소");
        String description = LabValidation.requiredText(input.description(), LONG_TEXT, "상황 설명");
        String immediateAction = LabValidation.text(input.immediateAction(), LONG_TEXT, "즉시 조치");

        LocalDateTime notifiedAt = LabValidation.dateTimeOrNull(input.guardianNotifiedAt(), "보호자 연락 시각");
        String methodText = LabValidation.text(input.guardianMethod(), 10, "보호자 연락 방법");
        if ((notifiedAt == null) != (methodText == null)) {
            throw LabValidation.bad("보호자 연락 시각과 방법은 함께 입력해 주세요");
        }
        String method = methodText == null ? null : LabValidation.oneOf(methodText, METHODS, "보호자 연락 방법");
        if (notifiedAt != null) {
            if (notifiedAt.isBefore(occurredAt)) {
                throw LabValidation.bad("보호자 연락 시각이 일어난 시각보다 앞일 수 없습니다");
            }
            if (notifiedAt.isAfter(now.plusMinutes(FUTURE_TOLERANCE_MINUTES))) {
                throw LabValidation.bad("보호자 연락 시각이 지금보다 뒤일 수 없습니다");
            }
        }
        String guardianResponse = LabValidation.text(input.guardianResponse(), RESPONSE_TEXT, "보호자 반응");
        String hospital = LabValidation.text(input.hospitalName(), PLACE, "병원 이름");
        String followUp = LabValidation.text(input.followUp(), LONG_TEXT, "후속 조치");
        String prevention = LabValidation.text(input.prevention(), LONG_TEXT, "재발 방지");
        String reporter = LabValidation.text(input.reporterName(), NAME, "보고자 이름");

        String status = input.status() == null || input.status().isBlank()
                ? LabCareEvent.OPEN : LabValidation.oneOf(input.status(), STATUSES, "상태");
        LocalDate closedOn = null;
        if (LabCareEvent.CLOSED.equals(status)) {
            if (followUp == null) {
                throw LabValidation.bad("후속 조치를 적어야 닫을 수 있습니다");
            }
            if (requiresGuardianNotice(kind, severity) && notifiedAt == null) {
                throw LabValidation.bad("보호자 연락을 기록해야 닫을 수 있습니다");
            }
            // 이미 닫힌 기록을 고칠 때(닫는 날을 다시 안 보내도) 처음 닫은 날을 오늘로 덮어쓰지 않는다
            closedOn = input.closedOn() != null ? LabValidation.plausibleDate(input.closedOn(), "닫은 날")
                    : event.getClosedOn() != null && LabCareEvent.CLOSED.equals(event.getStatus())
                    ? event.getClosedOn() : LabValidation.seoulToday();
        }
        event.fill(input.elderlyId(), occurredAt, kind, severity, place, description, immediateAction, notifiedAt,
                method, guardianResponse, hospital, followUp, prevention, status, closedOn, reporter,
                Boolean.TRUE.equals(input.aiAssisted()));
    }

    /** 웹의 requiresGuardianNotice와 같은 규칙 */
    static boolean requiresGuardianNotice(String kind, String severity) {
        return GUARDIAN_KINDS.contains(kind) || GUARDIAN_SEVERITIES.contains(severity);
    }

    private static LabCareEventDto.View toView(LabCareEvent e, String elderlyName) {
        return new LabCareEventDto.View(e.getId(), e.getElderlyId(), elderlyName,
                LabValidation.formatDateTime(e.getOccurredAt()), e.getKind(), e.getSeverity(), e.getPlace(),
                e.getDescription(), e.getImmediateAction(), LabValidation.formatDateTime(e.getGuardianNotifiedAt()),
                e.getGuardianMethod(), e.getGuardianResponse(), e.getHospitalName(), e.getFollowUp(),
                e.getPrevention(), e.getStatus(), e.getClosedOn(), e.getReporterName(), e.isAiAssisted(),
                e.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("사고 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
