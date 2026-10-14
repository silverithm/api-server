package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBathLogDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBathLog;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBathLogRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목욕 제공 일지(주·야간보호 목욕서비스 제공 일지 항목).
 *
 * <p>분(minutes)은 서버가 시작·끝 시각으로 계산한다 — 클라이언트가 보낸 분을 믿지 않는다. 끝이 시작보다 늦어야 하고
 * (자정을 넘기는 목욕은 없다) 180분을 넘으면 입력 실수로 본다. 미래 날짜는 아직 제공하지 않은 일이라 받지 않는다.
 * 목욕 전 상태·피부 관찰·메모는 건강 정보라 암호화한다. 같은 날 같은 어르신이 여러 번 해도 되므로 유일 키는 없다.
 */
@Service
public class LabBathLogService {

    static final int MAX_MINUTES = 180;
    private static final int NAME = 50;
    private static final int PRE_CHECK = 100;
    private static final int LONG_TEXT = 500;
    private static final Set<String> METHODS = Set.of("FULL_BATH", "BATH_CHAIR", "BED");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabBathLogRepository logs;

    public LabBathLogService(LabAccessService access, LabElderDirectory elders, LabBathLogRepository logs) {
        this.access = access;
        this.elders = elders;
        this.logs = logs;
    }

    /** 한 달치(month 필수), elderlyId를 주면 그 어르신만. 날짜·시작 시각순 */
    @Transactional(readOnly = true)
    public List<LabBathLogDto.View> list(String month, Long elderlyId) {
        Long companyId = access.requireCompanyId();
        YearMonth ym = LabValidation.yearMonth(month, "월");
        List<LabBathLog> rows;
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
            rows = logs.findByCompanyIdAndElderlyIdAndBathDateBetweenOrderByBathDateAscStartTimeAscIdAsc(companyId,
                    elderlyId, ym.atDay(1), ym.atEndOfMonth());
        } else {
            rows = logs.findByCompanyIdAndBathDateBetweenOrderByBathDateAscStartTimeAscIdAsc(companyId, ym.atDay(1),
                    ym.atEndOfMonth());
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream().map(l -> toView(l, names.getOrDefault(l.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabBathLogDto.View create(LabBathLogDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabBathLog log = new LabBathLog(companyId);
        apply(log, input, companyId);
        LabBathLog saved = logs.saveAndFlush(log);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public LabBathLogDto.View update(Long id, LabBathLogDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabBathLog log = logs.findByIdAndCompanyId(id, companyId).orElseThrow(LabBathLogService::notFound);
        apply(log, input, companyId);
        LabBathLog saved = logs.saveAndFlush(log);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        logs.delete(logs.findByIdAndCompanyId(id, companyId).orElseThrow(LabBathLogService::notFound));
    }

    private void apply(LabBathLog log, LabBathLogDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("목욕 일지 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        LocalDate day = LabValidation.plausibleDate(input.bathDate(), "목욕 날짜");
        if (day.isAfter(LabValidation.seoulToday())) {
            throw LabValidation.bad("목욕 날짜가 오늘보다 뒤일 수 없습니다");
        }
        String start = LabValidation.clock(input.startTime(), "시작 시각");
        String end = LabValidation.clock(input.endTime(), "끝 시각");
        long minutes = ChronoUnit.MINUTES.between(LocalTime.parse(start), LocalTime.parse(end));
        if (minutes <= 0) {
            throw LabValidation.bad("끝 시각이 시작 시각보다 늦어야 합니다");
        }
        if (minutes > MAX_MINUTES) {
            throw LabValidation.bad("목욕 시간은 " + MAX_MINUTES + "분을 넘을 수 없습니다");
        }
        String method = LabValidation.oneOf(input.method(), METHODS, "목욕 방법");
        String provider = LabValidation.requiredText(input.providerName(), NAME, "목욕을 도운 요양보호사 이름");
        log.fill(input.elderlyId(), day, start, end, (short) minutes, method, provider,
                LabValidation.text(input.preCheck(), PRE_CHECK, "목욕 전 상태"),
                LabValidation.text(input.skinNote(), LONG_TEXT, "피부·상처 관찰"),
                LabValidation.text(input.note(), LONG_TEXT, "메모"));
    }

    private static LabBathLogDto.View toView(LabBathLog l, String elderlyName) {
        return new LabBathLogDto.View(l.getId(), l.getElderlyId(), elderlyName, l.getBathDate(), l.getStartTime(),
                l.getEndTime(), l.getMinutes(), l.getMethod(), l.getProviderName(), l.getPreCheck(),
                l.getSkinNote(), l.getNote(), l.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("목욕 일지를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
