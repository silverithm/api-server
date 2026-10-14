package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabNursingLogDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabNursingLog;
import com.silverithm.vehicleplacementsystem.lab.repository.LabNursingLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 간호일지 — 어르신·날짜당 1건 저장(upsert), 날짜별/어르신 월별 조회, 삭제.
 *
 * <p>바이탈은 "잴 수 없는 값"이 아니라 "잘못 친 값"을 거르는 범위다(혈압 250 같은 건 입력 오류). 임상적 판정
 * (고혈압·발열 등)은 화면이 하고 서버는 저장 가능한 값인지만 본다. 수축기가 이완기보다 낮은 값은
 * 물리적으로 말이 안 되어(칸을 바꿔 적은 흔한 실수) 저장하지 않는다.
 */
@Service
public class LabNursingLogService {

    private static final int LONG_TEXT = 2000;
    private static final int NAME = 50;
    private static final Set<String> MEDS = Set.of("GIVEN", "SKIPPED", "NA");

    private static final BigDecimal TEMP_MIN = new BigDecimal("34.0");
    private static final BigDecimal TEMP_MAX = new BigDecimal("42.0");
    private static final BigDecimal WEIGHT_MIN = new BigDecimal("20.0");
    private static final BigDecimal WEIGHT_MAX = new BigDecimal("200.0");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabNursingLogRepository logs;
    private final TransactionTemplate tx;

    public LabNursingLogService(LabAccessService access, LabElderDirectory elders, LabNursingLogRepository logs,
                                PlatformTransactionManager txManager) {
        this.access = access;
        this.elders = elders;
        this.logs = logs;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 하루치 — 그날 일지가 있는 어르신들의 일지(이름순) */
    @Transactional(readOnly = true)
    public List<LabNursingLogDto.View> listByDate(String date) {
        Long companyId = access.requireCompanyId();
        LocalDate day = LabValidation.date(date, "날짜");
        Map<Long, String> names = elders.namesInCompany(companyId);
        return logs.findByCompanyIdAndLogDate(companyId, day).stream()
                .map(l -> toView(l, names.getOrDefault(l.getElderlyId(), "")))
                .sorted(Comparator.comparing(LabNursingLogDto.View::elderlyName)
                        .thenComparing(LabNursingLogDto.View::id))
                .toList();
    }

    /** 어르신 1명의 한 달치 — 날짜 오름차순(1일부터 말일까지 양 끝 포함) */
    @Transactional(readOnly = true)
    public List<LabNursingLogDto.View> listByElderAndMonth(Long elderlyId, String month) {
        Long companyId = access.requireCompanyId();
        YearMonth ym = LabValidation.yearMonth(month, "월");
        elders.requireInCompany(elderlyId, companyId);
        String name = elders.nameOf(elderlyId);
        return logs.findByCompanyIdAndElderlyIdAndLogDateBetweenOrderByLogDateAsc(
                        companyId, elderlyId, ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(l -> toView(l, name))
                .toList();
    }

    public LabNursingLogDto.View upsert(LabNursingLogDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("간호일지 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        LocalDate day = LabValidation.required(input.logDate(), "기록 날짜");

        Short systolic = range(input.systolic(), 50, 260, "수축기 혈압");
        Short diastolic = range(input.diastolic(), 30, 160, "이완기 혈압");
        if (systolic != null && diastolic != null && systolic < diastolic) {
            throw LabValidation.bad("수축기 혈압(" + systolic + ")이 이완기 혈압(" + diastolic
                    + ")보다 낮을 수 없습니다. 두 칸이 바뀌지 않았는지 확인해 주세요");
        }
        Short pulse = range(input.pulse(), 30, 200, "맥박");
        Short bloodSugar = range(input.bloodSugar(), 20, 600, "혈당");
        BigDecimal temperature = decimal(input.temperature(), TEMP_MIN, TEMP_MAX, "체온");
        BigDecimal weight = decimal(input.weight(), WEIGHT_MIN, WEIGHT_MAX, "체중");
        String medMorning = med(input.medMorning(), "아침 투약");
        String medLunch = med(input.medLunch(), "점심 투약");
        String medEvening = med(input.medEvening(), "저녁 투약");
        String treatment = LabValidation.text(input.treatment(), LONG_TEXT, "처치");
        String note = LabValidation.text(input.note(), LONG_TEXT, "간호 메모");
        String recorder = LabValidation.text(input.recorderName(), NAME, "기록자 이름");
        boolean ai = Boolean.TRUE.equals(input.aiAssisted());

        LabNursingLog saved = LabValidation.upsertWithRetry(tx, () -> {
            LabNursingLog log = logs.findByElderlyIdAndLogDate(input.elderlyId(), day)
                    .orElseGet(() -> new LabNursingLog(companyId, input.elderlyId(), day));
            log.fill(systolic, diastolic, pulse, temperature, bloodSugar, weight, medMorning, medLunch,
                    medEvening, treatment, note, recorder, ai);
            return logs.saveAndFlush(log);
        });
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        LabNursingLog log = logs.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("간호일지를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        logs.delete(log);
    }

    private static Short range(Integer value, int min, int max, String label) {
        if (value == null) {
            return null;
        }
        if (value < min || value > max) {
            throw LabValidation.bad(label + "은(는) " + min + "~" + max + " 사이여야 합니다 (입력값 " + value + ")");
        }
        return value.shortValue();
    }

    /** 소수 첫째 자리까지만 — 36.55처럼 열이 담지 못하는 값이 조용히 반올림되어 저장되지 않게 거절한다 */
    private static BigDecimal decimal(BigDecimal value, BigDecimal min, BigDecimal max, String label) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw LabValidation.bad(label + "은(는) " + min.toPlainString() + "~" + max.toPlainString()
                    + " 사이여야 합니다 (입력값 " + value.toPlainString() + ")");
        }
        if (value.stripTrailingZeros().scale() > 1) {
            throw LabValidation.bad(label + "은(는) 소수 첫째 자리까지만 입력할 수 있습니다");
        }
        return value.setScale(1);
    }

    private static String med(String value, String label) {
        String text = LabValidation.text(value, 10, label);
        if (text == null) {
            return null;
        }
        if (!MEDS.contains(text)) {
            throw LabValidation.bad(label + " 값이 올바르지 않습니다 (GIVEN, SKIPPED, NA 중 하나)");
        }
        return text;
    }

    private static LabNursingLogDto.View toView(LabNursingLog l, String elderlyName) {
        return new LabNursingLogDto.View(l.getId(), l.getElderlyId(), elderlyName, l.getLogDate(),
                intOf(l.getSystolic()), intOf(l.getDiastolic()), intOf(l.getPulse()), l.getTemperature(),
                intOf(l.getBloodSugar()), l.getWeight(), l.getMedMorning(), l.getMedLunch(), l.getMedEvening(),
                l.getTreatment(), l.getNote(), l.getRecorderName(), l.isAiAssisted(), l.getUpdatedAt());
    }

    private static Integer intOf(Short value) {
        return value == null ? null : value.intValue();
    }
}
