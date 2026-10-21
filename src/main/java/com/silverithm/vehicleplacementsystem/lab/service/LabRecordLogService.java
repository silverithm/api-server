package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabRecordLogDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabRecordLog;
import com.silverithm.vehicleplacementsystem.lab.repository.LabRecordLogRepository;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종류별 기록 — 업무수행일지(프로그램 관리자·선임 요양보호사), 시설운영일지, 수급자 안전관리 설명(연 1회), 현금영수증 발행.
 *
 * <p>종류마다 필수 칸·허용 체크 항목이 다르고 그 규칙을 {@link #KINDS}가 한곳에 모은다. 기록일은 오늘(서울) 이후일 수 없다.
 * 수급자별 종류(안전관리 설명·현금영수증)는 어르신이 이 기관 소속이어야 하고, 나머지는 어르신을 받지 않는다.
 * 조회는 종류와 함께 월·연도·어르신 중 하나가 필수다.
 */
@Service
public class LabRecordLogService {

    static final String PROGRAM_MANAGER_LOG = "PROGRAM_MANAGER_LOG";
    static final String SENIOR_CAREGIVER_LOG = "SENIOR_CAREGIVER_LOG";
    static final String FACILITY_DIARY = "FACILITY_DIARY";
    static final String SAFETY_BRIEFING = "SAFETY_BRIEFING";
    static final String CASH_RECEIPT = "CASH_RECEIPT";

    private static final int BODY = 2000;
    private static final int ACTION = 1000;
    private static final int EXTRA = 100;
    private static final long MAX_AMOUNT = 1_000_000_000L;

    /**
     * 종류별 규칙. checks는 허용 체크 항목, groups는 "항목 묶음에서 하나 이상" 요건(예: 안전관리 설명의 설명 대상),
     * 묶음 안 항목은 checks에도 들어 있어야 한다.
     */
    record Kind(String label, boolean perElder, boolean bodyRequired, boolean hasAmount, boolean hasExtra,
                Set<String> checks, List<Set<String>> requiredGroups, boolean exactlyOneCheck) {
    }

    static final Map<String, Kind> KINDS = new LinkedHashMap<>();

    static {
        KINDS.put(PROGRAM_MANAGER_LOG, new Kind("프로그램 관리자 업무수행일지", false, true, false, false,
                set("PROGRAM_PLAN", "COGNITIVE_CHECK", "PROVIDER_CHECK", "OPINION", "RECORD_REVIEW"), List.of(), false));
        KINDS.put(SENIOR_CAREGIVER_LOG, new Kind("선임 요양보호사 업무수행일지", false, true, false, false,
                set("FIELD_GUIDE", "STATUS_CHECK", "NOTICE", "EDUCATION", "RECORD_REVIEW"), List.of(), false));
        KINDS.put(FACILITY_DIARY, new Kind("시설운영일지", false, true, false, false,
                set("OPENING", "SANITATION", "FIRE_SAFETY", "MEAL", "VISITOR", "TRANSPORT"), List.of(), false));
        KINDS.put(SAFETY_BRIEFING, new Kind("수급자 안전관리 설명", true, false, false, false,
                set("FALL", "BEDSORE", "INFECTION", "EMERGENCY", "TRANSPORT", "MEDICATION", "TO_ELDER", "TO_GUARDIAN"),
                List.of(set("FALL", "BEDSORE", "INFECTION", "EMERGENCY", "TRANSPORT", "MEDICATION"),
                        set("TO_ELDER", "TO_GUARDIAN")), false));
        KINDS.put(CASH_RECEIPT, new Kind("현금영수증 발행", true, false, true, true,
                set("INCOME_DEDUCTION", "EXPENSE_PROOF"), List.of(), true));
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabRecordLogRepository entries;

    public LabRecordLogService(LabAccessService access, LabElderDirectory elders, LabRecordLogRepository entries) {
        this.access = access;
        this.elders = elders;
        this.entries = entries;
    }

    /** 기록일 최신순. 월(month)·연도(year)·어르신(elderlyId) 중 하나가 필수이고 함께 주면 모두 만족하는 것만 돌려준다 */
    @Transactional(readOnly = true)
    public List<LabRecordLogDto.View> list(String kind, String month, Integer year, Long elderlyId) {
        Long companyId = access.requireCompanyId();
        Kind rule = kindOf(kind);
        boolean byMonth = month != null && !month.isBlank();
        if (!byMonth && year == null && elderlyId == null) {
            throw LabValidation.bad("조회할 월(month), 연도(year), 어르신(elderlyId) 중 하나를 보내 주세요");
        }
        YearMonth ym = byMonth ? LabValidation.yearMonth(month, "월") : null;
        if (year != null && (year < 2000 || year > 2100)) {
            throw LabValidation.bad("연도는 2000~2100 사이여야 합니다");
        }
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
        }

        List<LabRecordLog> rows;
        if (elderlyId != null) {
            rows = entries.findByCompanyIdAndKindAndElderlyIdOrderByEntryDateDescIdDesc(companyId, kind, elderlyId);
        } else if (ym != null) {
            rows = entries.findByCompanyIdAndKindAndEntryDateBetweenOrderByEntryDateDescIdDesc(
                    companyId, kind, ym.atDay(1), ym.atEndOfMonth());
        } else {
            rows = entries.findByCompanyIdAndKindAndEntryDateBetweenOrderByEntryDateDescIdDesc(
                    companyId, kind, Year.of(year).atDay(1), Year.of(year).atMonth(12).atEndOfMonth());
        }
        Map<Long, String> names = rule.perElder() ? elders.namesInCompany(companyId) : Map.of();
        return rows.stream()
                .filter(e -> year == null || e.getEntryDate().getYear() == year)
                .filter(e -> ym == null || YearMonth.from(e.getEntryDate()).equals(ym))
                .map(e -> toView(e, e.getElderlyId() == null ? "" : names.getOrDefault(e.getElderlyId(), "")))
                .toList();
    }

    @Transactional
    public LabRecordLogDto.View create(LabRecordLogDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("기록 내용을 보내 주세요");
        }
        kindOf(input.kind());
        LabRecordLog entry = new LabRecordLog(companyId, input.kind());
        apply(entry, input, companyId);
        return toView(entries.saveAndFlush(entry), nameOf(entry));
    }

    @Transactional
    public LabRecordLogDto.View update(Long id, LabRecordLogDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabRecordLog entry = entries.findByIdAndCompanyId(id, companyId).orElseThrow(LabRecordLogService::notFound);
        if (input == null) {
            throw LabValidation.bad("기록 내용을 보내 주세요");
        }
        if (input.kind() != null && !input.kind().equals(entry.getKind())) {
            throw LabValidation.bad("기록의 종류는 바꿀 수 없습니다");
        }
        apply(entry, input, companyId);
        return toView(entries.saveAndFlush(entry), nameOf(entry));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        entries.delete(entries.findByIdAndCompanyId(id, companyId).orElseThrow(LabRecordLogService::notFound));
    }

    private String nameOf(LabRecordLog entry) {
        return entry.getElderlyId() == null ? "" : elders.nameOf(entry.getElderlyId());
    }

    private void apply(LabRecordLog entry, LabRecordLogDto.Input input, Long companyId) {
        Kind rule = KINDS.get(entry.getKind());
        LocalDate day = LabValidation.plausibleDate(input.entryDate(), "기록일");
        if (day.isAfter(LabValidation.seoulToday())) {
            throw LabValidation.bad("기록일은 오늘 이후일 수 없습니다");
        }
        Long elderlyId = null;
        if (rule.perElder()) {
            elderlyId = LabValidation.required(input.elderlyId(), "어르신");
            elders.requireInCompany(elderlyId, companyId);
        } else if (input.elderlyId() != null) {
            throw LabValidation.bad(rule.label() + "에는 어르신을 지정하지 않습니다");
        }
        String author = LabValidation.requiredText(input.authorName(), 50, "작성자 이름");
        List<String> checks = checks(rule, input.checks());
        String body = rule.bodyRequired()
                ? LabValidation.requiredText(input.body(), BODY, "내용")
                : LabValidation.text(input.body(), BODY, "내용");
        String action = LabValidation.text(input.action(), ACTION, "조치");
        String extra = null;
        if (rule.hasExtra()) {
            extra = LabValidation.text(input.extra(), EXTRA, "승인번호");
        } else if (input.extra() != null && !input.extra().isBlank()) {
            throw LabValidation.bad(rule.label() + "에는 승인번호 칸이 없습니다");
        }
        Long amount = null;
        if (rule.hasAmount()) {
            amount = LabValidation.required(input.amount(), "금액");
            if (amount < 1 || amount > MAX_AMOUNT) {
                throw LabValidation.bad("금액은 1원 이상 " + MAX_AMOUNT + "원 이하여야 합니다");
            }
        } else if (input.amount() != null) {
            throw LabValidation.bad(rule.label() + "에는 금액 칸이 없습니다");
        }
        entry.fill(day, elderlyId, author, String.join(",", checks), body, action, extra, amount);
    }

    /** 허용 항목만, 중복 없이, 허용 목록의 순서대로. 종류가 요구하는 묶음(하나 이상)·단일 선택도 확인한다 */
    private static List<String> checks(Kind rule, List<String> input) {
        Set<String> chosen = new LinkedHashSet<>();
        if (input != null) {
            for (String code : input) {
                if (code == null || !rule.checks().contains(code)) {
                    throw LabValidation.bad("확인 항목이 올바르지 않습니다");
                }
                chosen.add(code);
            }
        }
        if (rule.exactlyOneCheck() && chosen.size() != 1) {
            throw LabValidation.bad("구분을 하나 골라 주세요");
        }
        for (Set<String> group : rule.requiredGroups()) {
            if (group.stream().noneMatch(chosen::contains)) {
                throw LabValidation.bad(group.contains("TO_ELDER") ? "설명한 대상(수급자·보호자)을 하나 이상 골라 주세요"
                        : "설명한 안전 항목을 하나 이상 골라 주세요");
            }
        }
        List<String> ordered = new ArrayList<>();
        for (String code : rule.checks()) {
            if (chosen.contains(code)) {
                ordered.add(code);
            }
        }
        return ordered;
    }

    private static Kind kindOf(String kind) {
        Kind rule = kind == null ? null : KINDS.get(kind);
        if (rule == null) {
            throw LabValidation.bad("기록 종류가 올바르지 않습니다");
        }
        return rule;
    }

    private static LabRecordLogDto.View toView(LabRecordLog e, String elderlyName) {
        List<String> checks = e.getChecks() == null || e.getChecks().isBlank() ? List.of()
                : List.of(e.getChecks().split(","));
        return new LabRecordLogDto.View(e.getId(), e.getKind(), e.getEntryDate(), e.getElderlyId(), elderlyName,
                e.getAuthorName(), checks, e.getBody(), e.getAction(), e.getExtra(), e.getAmount(), e.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
