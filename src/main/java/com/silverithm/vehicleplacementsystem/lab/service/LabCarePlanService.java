package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCarePlanDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCarePlan;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCarePlanRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 욕구사정·급여제공계획 — 어르신·연도당 1건 저장(upsert), 연도별 조회, 삭제.
 *
 * <p>욕구(needs)·목표·급여 내용(services)은 암호화 열이다. services는 JSON 배열 하나로 암호화되어 들어가는데
 * 암호문이 평문의 약 4.2배라 TEXT(64KB)를 넘지 않도록 항목 수(30)와 칸별 길이를 함께 제한한다
 * (최대치로 채워도 약 51KB). 한 줄도 비어 있지 않은 행만 저장한다.
 */
@Service
public class LabCarePlanService {

    static final int MAX_SERVICES = 30;
    private static final int NEED = 1000;
    private static final int GOALS = 2000;
    private static final int AREA = 30;
    private static final int SERVICE_CONTENT = 300;
    private static final int FREQUENCY = 40;
    private static final int NAME = 50;

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabCarePlanRepository plans;
    private final TransactionTemplate tx;

    public LabCarePlanService(LabAccessService access, LabElderDirectory elders, LabCarePlanRepository plans,
                              PlatformTransactionManager txManager) {
        this.access = access;
        this.elders = elders;
        this.plans = plans;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 그 해 계획 — 계획이 있는 어르신만(없는 분은 웹이 "계획 없음"으로 본다). 이름순. */
    @Transactional(readOnly = true)
    public List<LabCarePlanDto.View> listByYear(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        Map<Long, String> names = elders.namesInCompany(companyId);
        return plans.findByCompanyIdAndPlanYear(companyId, (short) y).stream()
                .map(p -> toView(p, names.getOrDefault(p.getElderlyId(), "")))
                .sorted(Comparator.comparing(LabCarePlanDto.View::elderlyName)
                        .thenComparing(LabCarePlanDto.View::id))
                .toList();
    }

    public LabCarePlanDto.View upsert(LabCarePlanDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("급여제공계획 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        Short year = (short) LabValidation.year(input.planYear(), "계획 연도");
        LocalDate assessedOn = LabValidation.required(input.assessedOn(), "욕구사정일");
        String needsJson = LabJson.write(cleanNeeds(input.needs()));
        String goals = LabValidation.text(input.goals(), GOALS, "급여제공 목표");
        List<LabCarePlanDto.ServiceItem> services = cleanServices(input.services());
        // 비어 있으면 null로 두어 암호화 열에 "[]"만 들어가는 일을 피한다
        String servicesJson = services.isEmpty() ? null : LabJson.write(services);
        String writer = LabValidation.text(input.writerName(), NAME, "작성자 이름");
        boolean ai = Boolean.TRUE.equals(input.aiAssisted());
        LocalDate agreedOn = input.guardianAgreedOn();

        LabCarePlan saved = LabValidation.upsertWithRetry(tx, () -> {
            LabCarePlan plan = plans.findByElderlyIdAndPlanYear(input.elderlyId(), year)
                    .orElseGet(() -> new LabCarePlan(companyId, input.elderlyId(), year));
            plan.fill(assessedOn, needsJson, goals, servicesJson, agreedOn, writer, ai);
            return plans.saveAndFlush(plan);
        });
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        LabCarePlan plan = plans.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("급여제공계획을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        plans.delete(plan);
    }

    /** 여섯 영역을 모두 가진 객체로 정리한다(빈 칸은 null) — 읽는 쪽이 키 유무를 따지지 않게 */
    private static LabCarePlanDto.Needs cleanNeeds(LabCarePlanDto.Needs needs) {
        if (needs == null) {
            return new LabCarePlanDto.Needs(null, null, null, null, null, null);
        }
        return new LabCarePlanDto.Needs(
                LabValidation.text(needs.physical(), NEED, "신체 기능 욕구"),
                LabValidation.text(needs.cognition(), NEED, "인지 기능 욕구"),
                LabValidation.text(needs.nutrition(), NEED, "영양 상태 욕구"),
                LabValidation.text(needs.communication(), NEED, "의사소통 욕구"),
                LabValidation.text(needs.family(), NEED, "가족·환경 욕구"),
                LabValidation.text(needs.other(), NEED, "기타 욕구"));
    }

    private static List<LabCarePlanDto.ServiceItem> cleanServices(List<LabCarePlanDto.ServiceItem> raw) {
        List<LabCarePlanDto.ServiceItem> items = new ArrayList<>();
        if (raw == null) {
            return items;
        }
        if (raw.size() > MAX_SERVICES) {
            throw LabValidation.bad("급여제공 내용은 " + MAX_SERVICES + "개까지 넣을 수 있습니다");
        }
        for (LabCarePlanDto.ServiceItem item : raw) {
            if (item == null) {
                continue;
            }
            String area = LabValidation.text(item.area(), AREA, "급여 영역");
            String content = LabValidation.text(item.content(), SERVICE_CONTENT, "급여 내용");
            String frequency = LabValidation.text(item.frequency(), FREQUENCY, "제공 빈도");
            if (area == null && content == null && frequency == null) {
                continue; // 화면의 빈 줄
            }
            if (content == null) {
                throw LabValidation.bad("급여 내용을 입력해 주세요 (영역 또는 빈도만 적은 줄이 있습니다)");
            }
            items.add(new LabCarePlanDto.ServiceItem(area, content, frequency));
        }
        return items;
    }

    private static LabCarePlanDto.View toView(LabCarePlan p, String elderlyName) {
        return new LabCarePlanDto.View(p.getId(), p.getElderlyId(), elderlyName, p.getPlanYear(),
                p.getAssessedOn(), LabJson.readNeeds(p.getNeeds()), p.getGoals(),
                LabJson.readServices(p.getServices()), p.getGuardianAgreedOn(), p.getWriterName(),
                p.isAiAssisted(), p.getUpdatedAt());
    }
}
