package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffingDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffingMonth;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffingMonthRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 월 인력배치 점검(평가 3 인력기준) — 그 달의 입소자 수 합·공휴일·직원별 근무시간을 기관 x 달 한 건으로 저장한다.
 *
 * <p>기관 x 달에 한 건이라 PUT이 upsert다. 기준 인원·결원·감산 점수 계산은 웹이 하고 서버는 입력이 말이 되는지만
 * 지킨다. 직원 목록은 JSON 문자열 컬럼(암호화)에 담기므로 받은 값을 그대로 저장하지 않고 검증·정리한 값만
 * 다시 직렬화한다. memberId를 주면 같은 기관 직원이어야 하고, 하나라도 아니면 통째로 거절한다.
 */
@Service
public class LabStaffingService {

    static final Set<String> JOB_TYPES = Set.of("DIRECTOR", "SOCIAL_WORKER", "NURSE", "NURSE_AIDE", "THERAPIST",
            "CAREGIVER", "OFFICE", "COOK", "DRIVER", "OTHER");
    static final int MAX_STAFF = 100;
    static final int MAX_HOLIDAYS = 31;
    private static final long MAX_USERS_SUM = 100_000L;
    private static final BigDecimal MAX_HOURS = BigDecimal.valueOf(400);
    private static final int NAME = 50;

    private final LabAccessService access;
    private final LabStaffService staffDirectory;
    private final LabStaffingMonthRepository months;
    private final TransactionTemplate tx;

    public LabStaffingService(LabAccessService access, LabStaffService staffDirectory,
                              LabStaffingMonthRepository months, PlatformTransactionManager txManager) {
        this.access = access;
        this.staffDirectory = staffDirectory;
        this.months = months;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 그 달 기록 — 없으면 404 */
    @Transactional(readOnly = true)
    public LabStaffingDto.View getByMonth(String month) {
        Long companyId = access.requireCompanyId();
        String m = LabValidation.month(month, "월");
        return months.findByCompanyIdAndStaffingMonth(companyId, m).map(LabStaffingService::toView)
                .orElseThrow(() -> new CustomException("해당 월 인력배치 기록이 없습니다", HttpStatus.NOT_FOUND));
    }

    /** 그 해 기록 전부(달 오름차순) */
    @Transactional(readOnly = true)
    public List<LabStaffingDto.View> listByYear(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        return months.findByCompanyIdAndStaffingMonthStartingWithOrderByStaffingMonthAsc(companyId, y + "-")
                .stream().map(LabStaffingService::toView).toList();
    }

    public LabStaffingDto.View upsert(LabStaffingDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("인력배치 내용을 보내 주세요");
        }
        String month = LabValidation.month(input.staffingMonth(), "인력배치 월");
        YearMonth ym = YearMonth.parse(month);
        LabValidation.plausibleDate(ym.atDay(1), "인력배치 월");
        short serviceDays = (short) LabValidation.longInRange(input.serviceDays(), 1, 31, "운영일수");
        int usersSum = (int) LabValidation.longInRange(input.usersSum(), 0, MAX_USERS_SUM, "입소자 수 합");
        String extraNote = LabValidation.text(input.extraUsersNote(), 200, "입소자 수 가산 사유");
        String holidays = LabJson.write(holidays(input.holidays(), ym));
        List<LabStaffingDto.Staff> staff = staff(input.staff(), companyId);
        String note = LabValidation.text(input.note(), 500, "메모");
        String writer = LabValidation.text(input.writerName(), NAME, "작성자 이름");
        String staffJson = LabJson.write(staff);

        LabStaffingMonth saved = LabValidation.upsertWithRetry(tx, () -> {
            LabStaffingMonth row = months.findByCompanyIdAndStaffingMonth(companyId, month)
                    .orElseGet(() -> new LabStaffingMonth(companyId, month));
            row.fill(serviceDays, usersSum, extraNote, holidays, staffJson, note, writer);
            return months.saveAndFlush(row);
        });
        return toView(saved);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        months.delete(months.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("인력배치 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    /** 공휴일 — 그 달 날짜만, 31개 이하. 중복은 합치고 날짜순으로 저장한다 */
    private static List<String> holidays(List<String> input, YearMonth month) {
        if (input == null) {
            return List.of();
        }
        if (input.size() > MAX_HOLIDAYS) {
            throw LabValidation.bad("공휴일은 최대 " + MAX_HOLIDAYS + "개까지 입력할 수 있습니다");
        }
        Set<LocalDate> days = new TreeSet<>();
        for (String text : input) {
            LocalDate day = LabValidation.date(text, "공휴일");
            if (!YearMonth.from(day).equals(month)) {
                throw LabValidation.bad("공휴일은 " + month + " 안의 날짜여야 합니다: " + day);
            }
            days.add(day);
        }
        return days.stream().map(LocalDate::toString).toList();
    }

    /** 직원 목록 — 직종·시간·이름을 검증하고, memberId가 있으면 같은 기관 직원인지 한 번에 확인한다 */
    private List<LabStaffingDto.Staff> staff(List<LabStaffingDto.Staff> input, Long companyId) {
        if (input == null) {
            return List.of();
        }
        if (input.size() > MAX_STAFF) {
            throw LabValidation.bad("직원은 최대 " + MAX_STAFF + "명까지 입력할 수 있습니다");
        }
        List<LabStaffingDto.Staff> clean = new ArrayList<>();
        Set<Long> memberIds = new TreeSet<>();
        for (LabStaffingDto.Staff s : input) {
            if (s == null) {
                throw LabValidation.bad("직원 목록에 올바르지 않은 값이 있습니다");
            }
            String name = LabValidation.requiredText(s.name(), NAME, "직원 이름");
            String jobType = LabValidation.oneOf(s.jobType(), JOB_TYPES, "직종");
            BigDecimal hours = hours(s.hours());
            if (s.memberId() != null) {
                memberIds.add(s.memberId());
            }
            clean.add(new LabStaffingDto.Staff(s.memberId(), name, jobType, hours));
        }
        if (!memberIds.isEmpty()) {
            staffDirectory.requireAllInCompany(memberIds, companyId);
        }
        return clean;
    }

    /** 0~400시간, 소수 첫째 자리까지. 둘째 자리부터 값이 있으면 조용히 반올림하지 않고 400 */
    private static BigDecimal hours(BigDecimal value) {
        if (value == null) {
            throw LabValidation.bad("근무시간을 입력해 주세요");
        }
        if (value.signum() < 0 || value.compareTo(MAX_HOURS) > 0) {
            throw LabValidation.bad("근무시간은 0~400 사이여야 합니다");
        }
        if (value.stripTrailingZeros().scale() > 1) {
            throw LabValidation.bad("근무시간은 소수 첫째 자리까지 입력할 수 있습니다");
        }
        return value.setScale(1);
    }

    private static LabStaffingDto.View toView(LabStaffingMonth m) {
        return new LabStaffingDto.View(m.getId(), m.getStaffingMonth(), m.getServiceDays(), m.getUsersSum(),
                m.getExtraUsersNote(), LabJson.readStrings(m.getHolidays()),
                LabJson.readStaffingStaff(m.getStaff()), m.getNote(), m.getWriterName(), m.getUpdatedAt());
    }
}
