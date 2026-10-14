package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicalVisitDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicalVisit;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicalVisitRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진료 동행 기록(평가 30 기준 1) — 급여제공시간 중 직원이 병원에 동행한 진료의 내용을 남긴다.
 *
 * <p>진료일은 오늘(서울) 이후일 수 없고, 시각은 둘 다 있으면 끝이 시작보다 늦어야 한다. 진료 내용과 동행 직원은
 * 필수다(평가에서 보는 항목). 진료 사유·내용·처방·보호자 메모는 암호화한다.
 * 조회는 월·어르신·후속 확인 예정 중 하나가 필수이고, 함께 주면 모두 만족하는 것만 돌려준다.
 */
@Service
public class LabMedicalVisitService {

    private static final int REASON = 1000;
    private static final int RESULT = 2000;
    private static final int PRESCRIPTION = 1000;
    private static final int GUARDIAN_NOTE = 100;

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabMedicalVisitRepository visits;

    public LabMedicalVisitService(LabAccessService access, LabElderDirectory elders,
                                  LabMedicalVisitRepository visits) {
        this.access = access;
        this.elders = elders;
        this.visits = visits;
    }

    /** 진료일 최신순. followUpDue는 후속 확인일이 오늘(서울) 이하이고 아직 안 한 것 */
    @Transactional(readOnly = true)
    public List<LabMedicalVisitDto.View> list(String month, Long elderlyId, Boolean followUpDue) {
        Long companyId = access.requireCompanyId();
        boolean due = Boolean.TRUE.equals(followUpDue);
        boolean byMonth = month != null && !month.isBlank();
        if (!due && !byMonth && elderlyId == null) {
            throw LabValidation.bad("조회할 월(month), 어르신(elderlyId), 후속 확인(followUpDue) 중 하나를 보내 주세요");
        }
        YearMonth ym = byMonth ? LabValidation.yearMonth(month, "월") : null;
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
        }

        // 가장 좁은 조건으로 읽고 나머지는 메모리에서 거른다
        List<LabMedicalVisit> rows;
        if (due) {
            rows = visits.findByCompanyIdAndFollowUpDoneFalseAndFollowUpOnLessThanEqualOrderByVisitDateDescIdDesc(
                    companyId, LabValidation.seoulToday());
        } else if (elderlyId != null) {
            rows = visits.findByCompanyIdAndElderlyIdOrderByVisitDateDescIdDesc(companyId, elderlyId);
        } else {
            rows = visits.findByCompanyIdAndVisitDateBetweenOrderByVisitDateDescIdDesc(
                    companyId, ym.atDay(1), ym.atEndOfMonth());
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream()
                .filter(v -> elderlyId == null || elderlyId.equals(v.getElderlyId()))
                .filter(v -> ym == null || YearMonth.from(v.getVisitDate()).equals(ym))
                .map(v -> toView(v, names.getOrDefault(v.getElderlyId(), "")))
                .toList();
    }

    @Transactional
    public LabMedicalVisitDto.View create(LabMedicalVisitDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabMedicalVisit visit = new LabMedicalVisit(companyId);
        apply(visit, input, companyId);
        return toView(visits.saveAndFlush(visit), elders.nameOf(visit.getElderlyId()));
    }

    @Transactional
    public LabMedicalVisitDto.View update(Long id, LabMedicalVisitDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabMedicalVisit visit = visits.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabMedicalVisitService::notFound);
        apply(visit, input, companyId);
        return toView(visits.saveAndFlush(visit), elders.nameOf(visit.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        visits.delete(visits.findByIdAndCompanyId(id, companyId).orElseThrow(LabMedicalVisitService::notFound));
    }

    private void apply(LabMedicalVisit visit, LabMedicalVisitDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("진료 동행 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        LocalDate visitDate = LabValidation.plausibleDate(input.visitDate(), "진료일");
        if (visitDate.isAfter(LabValidation.seoulToday())) {
            throw LabValidation.bad("진료일은 오늘 이후일 수 없습니다");
        }
        String start = clockOrNull(input.startTime(), "진료 시작 시각");
        String end = clockOrNull(input.endTime(), "진료 종료 시각");
        // HH:MM은 자릿수가 고정이라 문자열 비교가 시각 비교와 같다
        if (start != null && end != null && end.compareTo(start) <= 0) {
            throw LabValidation.bad("진료 종료 시각은 시작 시각보다 늦어야 합니다");
        }
        String hospital = LabValidation.requiredText(input.hospital(), 100, "병원");
        String department = LabValidation.text(input.department(), 40, "진료과");
        String reason = LabValidation.text(input.reason(), REASON, "진료 사유");
        String result = LabValidation.requiredText(input.result(), RESULT, "진료 내용");
        String prescription = LabValidation.text(input.prescription(), PRESCRIPTION, "처방");
        String escort = LabValidation.requiredText(input.escortName(), 50, "동행 직원");
        String guardianNote = LabValidation.text(input.guardianNote(), GUARDIAN_NOTE, "보호자 알림 메모");
        LocalDate followUpOn = input.followUpOn() == null ? null
                : LabValidation.plausibleDate(input.followUpOn(), "후속 확인일");
        visit.fill(input.elderlyId(), visitDate, start, end, hospital, department, reason, result, prescription,
                escort, Boolean.TRUE.equals(input.guardianNotified()), guardianNote, followUpOn,
                Boolean.TRUE.equals(input.followUpDone()), Boolean.TRUE.equals(input.aiAssisted()));
    }

    private static String clockOrNull(String value, String label) {
        return value == null || value.isBlank() ? null : LabValidation.clock(value, label);
    }

    private static LabMedicalVisitDto.View toView(LabMedicalVisit v, String elderlyName) {
        return new LabMedicalVisitDto.View(v.getId(), v.getElderlyId(), elderlyName, v.getVisitDate(),
                v.getStartTime(), v.getEndTime(), v.getHospital(), v.getDepartment(), v.getReason(), v.getResult(),
                v.getPrescription(), v.getEscortName(), v.isGuardianNotified(), v.getGuardianNote(),
                v.getFollowUpOn(), v.isFollowUpDone(), v.isAiAssisted(), v.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("진료 동행 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
