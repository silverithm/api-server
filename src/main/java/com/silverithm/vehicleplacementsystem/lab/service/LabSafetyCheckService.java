package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabSafetyCheckDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabSafetyCheck;
import com.silverithm.vehicleplacementsystem.lab.repository.LabSafetyCheckRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 위생·안전·시설 점검 기록 — 기관 x 종류 x 날짜 1건. 항목 코드·적합 여부·메모를 JSON으로 담는다.
 * 항목은 60개 이하, code는 영문 대문자·숫자·밑줄 2~30자, 메모는 200자 이하다.
 */
@Service
public class LabSafetyCheckService {

    static final int MAX_ITEMS = 60;
    private static final int NOTE = 200;
    private static final int ACTIONS = 2000;
    private static final int NAME = 50;
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{2,30}$");
    static final Set<String> KINDS = Set.of("KITCHEN", "HYGIENE", "FIRE", "FACILITY", "VEHICLE", "INFECTION",
            "DRILL");

    private final LabAccessService access;
    private final LabSafetyCheckRepository checks;
    private final TransactionTemplate tx;

    public LabSafetyCheckService(LabAccessService access, LabSafetyCheckRepository checks,
                                 PlatformTransactionManager txManager) {
        this.access = access;
        this.checks = checks;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 한 달치(1일~말일 양 끝 포함). kind를 주면 그 종류만 */
    @Transactional(readOnly = true)
    public List<LabSafetyCheckDto.View> listByMonth(String month, String kind) {
        Long companyId = access.requireCompanyId();
        YearMonth ym = LabValidation.yearMonth(month, "월");
        return range(companyId, kind, ym.atDay(1), ym.atEndOfMonth());
    }

    /** 한 해치(1월 1일~12월 31일 양 끝 포함) — 재난훈련 연 2회 확인용. kind를 주면 그 종류만 */
    @Transactional(readOnly = true)
    public List<LabSafetyCheckDto.View> listByYear(String year, String kind) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        return range(companyId, kind, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
    }

    private List<LabSafetyCheckDto.View> range(Long companyId, String kind, LocalDate from, LocalDate to) {
        List<LabSafetyCheck> rows = (kind == null || kind.isBlank())
                ? checks.findByCompanyIdAndCheckDateBetweenOrderByCheckDateAscKindAscIdAsc(companyId, from, to)
                : checks.findByCompanyIdAndKindAndCheckDateBetweenOrderByCheckDateAscIdAsc(companyId,
                        LabValidation.oneOf(kind, KINDS, "점검 종류"), from, to);
        return rows.stream().map(LabSafetyCheckService::toView).toList();
    }

    public LabSafetyCheckDto.View upsert(LabSafetyCheckDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        if (input == null) {
            throw LabValidation.bad("점검 내용을 보내 주세요");
        }
        String kind = LabValidation.oneOf(input.kind(), KINDS, "점검 종류");
        LocalDate date = LabValidation.plausibleDate(input.checkDate(), "점검일");
        String items = LabJson.write(items(input.items()));
        String actions = LabValidation.text(input.actions(), ACTIONS, "조치 내용");
        String inspector = LabValidation.text(input.inspectorName(), NAME, "점검자 이름");
        boolean ai = Boolean.TRUE.equals(input.aiAssisted());

        LabSafetyCheck saved = LabValidation.upsertWithRetry(tx, () -> {
            LabSafetyCheck check = checks.findByCompanyIdAndKindAndCheckDate(caller.companyId(), kind, date)
                    .orElseGet(() -> new LabSafetyCheck(caller.companyId(), kind, date));
            check.fill(items, actions, inspector, ai);
            return checks.saveAndFlush(check);
        });
        return toView(saved);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        checks.delete(checks.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("점검 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    /** 항목 목록 검증 — null이면 400, 60개 초과·null 원소·코드 형식·중복 코드·긴 메모는 400 */
    private static List<LabSafetyCheckDto.Item> items(List<LabSafetyCheckDto.Item> input) {
        if (input == null) {
            throw LabValidation.bad("점검 항목을 보내 주세요");
        }
        if (input.size() > MAX_ITEMS) {
            throw LabValidation.bad("점검 항목은 최대 " + MAX_ITEMS + "개까지 쓸 수 있습니다");
        }
        Set<String> seen = new HashSet<>();
        List<LabSafetyCheckDto.Item> result = new ArrayList<>(input.size());
        for (LabSafetyCheckDto.Item item : input) {
            if (item == null) {
                throw LabValidation.bad("점검 항목 목록에 올바르지 않은 값이 있습니다");
            }
            String code = item.code() == null ? null : item.code().strip();
            if (code == null || !CODE.matcher(code).matches()) {
                throw LabValidation.bad("점검 항목 코드가 올바르지 않습니다 (영문 대문자·숫자·밑줄 2~30자)");
            }
            if (!seen.add(code)) {
                throw LabValidation.bad("점검 항목 코드가 겹칩니다: " + code);
            }
            result.add(new LabSafetyCheckDto.Item(code, item.ok(), LabValidation.text(item.note(), NOTE, "항목 메모")));
        }
        return result;
    }

    private static LabSafetyCheckDto.View toView(LabSafetyCheck c) {
        return new LabSafetyCheckDto.View(c.getId(), c.getCheckDate(), c.getKind(),
                LabJson.readSafetyItems(c.getItems()), c.getActions(), c.getInspectorName(), c.isAiAssisted(),
                c.getUpdatedAt());
    }
}
