package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabVolunteerLogDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabVolunteerLog;
import com.silverithm.vehicleplacementsystem.lab.repository.LabVolunteerLogRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 봉사자 활동 일지 — 등록·수정·삭제와 월별/연도별 조회(날짜순).
 *
 * <p>봉사 시간은 클라이언트가 보낸 값을 믿지 않고 시작·끝 시각으로 서버가 계산한다(같은 날 안에서만).
 * 끝이 시작보다 늦지 않으면 400, 하루 720분(12시간)을 넘어도 400이다. 봉사자 이름과 활동 내용은 암호화된다.
 */
@Service
public class LabVolunteerLogService {

    static final int MAX_MINUTES = 720;
    private static final int NAME = 50;
    private static final int ORGANIZATION = 100;
    private static final int CONTENT = 1000;
    private static final Set<String> TYPES = Set.of("MEAL", "PROGRAM", "COMPANION", "CLEANING", "TALENT", "OTHER");

    private final LabAccessService access;
    private final LabVolunteerLogRepository logs;

    public LabVolunteerLogService(LabAccessService access, LabVolunteerLogRepository logs) {
        this.access = access;
        this.logs = logs;
    }

    /** month 또는 year 중 하나는 필요하다(둘 다 있으면 month). 날짜·시작 시각순 */
    @Transactional(readOnly = true)
    public List<LabVolunteerLogDto.View> list(String month, String year) {
        Long companyId = access.requireCompanyId();
        LocalDate from;
        LocalDate to;
        if (month != null) {
            YearMonth ym = LabValidation.yearMonth(month, "월");
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        } else if (year != null) {
            int y = LabValidation.year(year, "연도");
            from = LocalDate.of(y, 1, 1);
            to = LocalDate.of(y, 12, 31);
        } else {
            throw LabValidation.bad("월(YYYY-MM) 또는 연도(YYYY)를 지정해 주세요");
        }
        return logs.findByCompanyIdAndActivityDateBetweenOrderByActivityDateAscStartTimeAscIdAsc(companyId, from, to)
                .stream().map(LabVolunteerLogService::toView).toList();
    }

    @Transactional
    public LabVolunteerLogDto.View create(LabVolunteerLogDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabVolunteerLog log = new LabVolunteerLog(companyId);
        apply(log, input);
        return toView(logs.saveAndFlush(log));
    }

    @Transactional
    public LabVolunteerLogDto.View update(Long id, LabVolunteerLogDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabVolunteerLog log = logs.findByIdAndCompanyId(id, companyId).orElseThrow(LabVolunteerLogService::notFound);
        apply(log, input);
        return toView(logs.saveAndFlush(log));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        logs.delete(logs.findByIdAndCompanyId(id, companyId).orElseThrow(LabVolunteerLogService::notFound));
    }

    private void apply(LabVolunteerLog log, LabVolunteerLogDto.Input input) {
        if (input == null) {
            throw LabValidation.bad("봉사 내용을 보내 주세요");
        }
        LocalDate day = LabValidation.plausibleDate(input.activityDate(), "활동 날짜");
        String name = LabValidation.requiredText(input.volunteerName(), NAME, "봉사자 이름");
        String organization = LabValidation.text(input.organization(), ORGANIZATION, "소속 기관");
        String start = LabValidation.clock(input.startTime(), "시작 시각");
        String end = LabValidation.clock(input.endTime(), "끝 시각");
        long minutes = ChronoUnit.MINUTES.between(LocalTime.parse(start), LocalTime.parse(end));
        if (minutes <= 0) {
            throw LabValidation.bad("끝 시각은 시작 시각보다 늦어야 합니다");
        }
        if (minutes > MAX_MINUTES) {
            throw LabValidation.bad("봉사 시간은 하루 " + MAX_MINUTES + "분(12시간)을 넘을 수 없습니다");
        }
        String type = LabValidation.oneOf(input.activityType(), TYPES, "활동 종류");
        String content = LabValidation.text(input.content(), CONTENT, "활동 내용");
        String confirmer = LabValidation.text(input.confirmerName(), NAME, "확인자 이름");
        log.fill(day, name, organization, start, end, (short) minutes, type, content,
                Boolean.TRUE.equals(input.vmsRegistered()), confirmer);
    }

    private static LabVolunteerLogDto.View toView(LabVolunteerLog l) {
        return new LabVolunteerLogDto.View(l.getId(), l.getActivityDate(), l.getVolunteerName(),
                l.getOrganization(), l.getStartTime(), l.getEndTime(), l.getMinutes(), l.getActivityType(),
                l.getContent(), l.isVmsRegistered(), l.getConfirmerName(), l.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("봉사 일지를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
