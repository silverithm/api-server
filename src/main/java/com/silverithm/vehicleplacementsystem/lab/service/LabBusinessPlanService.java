package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBusinessPlanDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBusinessPlan;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBusinessPlanRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 연간 사업계획서(평가 2) — 기관 x 연도 1건, PUT이 upsert.
 *
 * <p>분야(기관운영·서비스 제공·직원관리)마다 항목 20개 이하. 작성 중에도 저장할 수 있게 항목의 칸은 비워 둘 수 있고
 * (빈 칸 점검은 화면이 한다), 길이와 예산(0 이상 정수)만 서버가 막는다. 받은 값을 그대로 저장하지 않고
 * 검증·정리한 값만 다시 직렬화한다.
 */
@Service
public class LabBusinessPlanService {

    static final int MAX_ITEMS_PER_SECTION = 20;
    private static final long MAX_ITEM_BUDGET = 10_000_000_000L;
    private static final long MAX_TOTAL = 10_000_000_000L;

    private final LabAccessService access;
    private final LabBusinessPlanRepository plans;
    private final TransactionTemplate tx;

    public LabBusinessPlanService(LabAccessService access, LabBusinessPlanRepository plans,
                                  PlatformTransactionManager txManager) {
        this.access = access;
        this.plans = plans;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 그 해 사업계획서 — 없으면 404 */
    @Transactional(readOnly = true)
    public LabBusinessPlanDto.View getByYear(String year) {
        Long companyId = access.requireCompanyId();
        short y = (short) LabValidation.year(year, "연도");
        return plans.findByCompanyIdAndPlanYear(companyId, y).map(LabBusinessPlanService::toView)
                .orElseThrow(() -> new CustomException("해당 연도 사업계획서가 없습니다", HttpStatus.NOT_FOUND));
    }

    public LabBusinessPlanDto.View upsert(LabBusinessPlanDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("사업계획 내용을 보내 주세요");
        }
        short year = (short) LabValidation.year(input.planYear(), "계획 연도");
        if (input.sections() == null) {
            throw LabValidation.bad("분야별 사업계획을 보내 주세요");
        }
        LabBusinessPlanDto.Sections source = input.sections();
        String sections = LabJson.write(new LabBusinessPlanDto.Sections(
                items(source.operation(), "기관운영"), items(source.service(), "서비스 제공"),
                items(source.staff(), "직원관리")));
        Long income = total(input.totalIncome(), "총수입");
        Long expense = total(input.totalExpense(), "총지출");
        LocalDate writtenOn = input.writtenOn() == null ? null
                : LabValidation.plausibleDate(input.writtenOn(), "작성일");
        String writer = LabValidation.text(input.writerName(), 50, "작성자 이름");
        boolean ai = Boolean.TRUE.equals(input.aiAssisted());

        LabBusinessPlan saved = LabValidation.upsertWithRetry(tx, () -> {
            LabBusinessPlan plan = plans.findByCompanyIdAndPlanYear(companyId, year)
                    .orElseGet(() -> new LabBusinessPlan(companyId, year));
            plan.fill(sections, income, expense, writtenOn, writer, ai);
            return plans.saveAndFlush(plan);
        });
        return toView(saved);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        plans.delete(plans.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("사업계획서를 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    private static Long total(Long value, String label) {
        return value == null ? null : LabValidation.longInRange(value, 0, MAX_TOTAL, label);
    }

    private static List<LabBusinessPlanDto.Item> items(List<LabBusinessPlanDto.Item> input, String section) {
        if (input == null) {
            return List.of();
        }
        if (input.size() > MAX_ITEMS_PER_SECTION) {
            throw LabValidation.bad(section + " 항목은 최대 " + MAX_ITEMS_PER_SECTION + "개까지 쓸 수 있습니다");
        }
        List<LabBusinessPlanDto.Item> result = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            LabBusinessPlanDto.Item item = input.get(i);
            String where = section + " " + (i + 1) + "번째 항목의 ";
            if (item == null) {
                throw LabValidation.bad(section + " " + (i + 1) + "번째 항목이 비어 있습니다");
            }
            Long budget = item.budget() == null ? null
                    : LabValidation.longInRange(item.budget(), 0, MAX_ITEM_BUDGET, where + "예산");
            result.add(new LabBusinessPlanDto.Item(LabValidation.text(item.name(), 60, where + "사업명"),
                    LabValidation.text(item.goal(), 300, where + "목표"),
                    LabValidation.text(item.content(), 1000, where + "내용"),
                    LabValidation.text(item.target(), 100, where + "대상"), budget,
                    LabValidation.text(item.schedule(), 100, where + "일정")));
        }
        return result;
    }

    private static LabBusinessPlanDto.View toView(LabBusinessPlan p) {
        return new LabBusinessPlanDto.View(p.getId(), p.getPlanYear(), LabJson.readBusinessSections(p.getSections()),
                p.getTotalIncome(), p.getTotalExpense(), p.getWrittenOn(), p.getWriterName(), p.isAiAssisted(),
                p.getUpdatedAt());
    }
}
