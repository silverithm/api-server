package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.lab.dto.LabEvalCheckDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabEvalCheck;
import com.silverithm.vehicleplacementsystem.lab.repository.LabEvalCheckRepository;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 기관평가 준비 점검 — 지표 코드별 준비 상태·근거·AI 검토 의견. 기관·지표당 1건. */
@Service
public class LabEvalCheckService {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{2,40}$");
    private static final Set<String> STATUSES = Set.of("READY", "PARTIAL", "MISSING", "NOT_APPLICABLE");

    private final LabAccessService access;
    private final LabEvalCheckRepository checks;
    private final TransactionTemplate tx;

    public LabEvalCheckService(LabAccessService access, LabEvalCheckRepository checks,
                               PlatformTransactionManager txManager) {
        this.access = access;
        this.checks = checks;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional(readOnly = true)
    public List<LabEvalCheckDto.View> list() {
        Long companyId = access.requireCompanyId();
        return checks.findByCompanyIdOrderByIndicatorCodeAsc(companyId).stream().map(LabEvalCheckService::toView)
                .toList();
    }

    public LabEvalCheckDto.View upsert(String indicatorCode, LabEvalCheckDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        if (indicatorCode == null || !CODE.matcher(indicatorCode).matches()) {
            throw LabValidation.bad("지표 코드가 올바르지 않습니다");
        }
        if (input == null) {
            throw LabValidation.bad("점검 내용을 보내 주세요");
        }
        String status = LabValidation.oneOf(input.status(), STATUSES, "준비 상태");
        String evidence = LabValidation.text(input.evidence(), 3000, "근거");
        String aiComment = LabValidation.text(input.aiComment(), 3000, "AI 의견");
        // 수정자 표기는 50자 컬럼에 맞춘다 — 긴 이메일 때문에 저장이 실패하면 안 된다
        String updatedBy = caller.username().length() > 50 ? caller.username().substring(0, 50) : caller.username();

        LabEvalCheck saved = LabValidation.upsertWithRetry(tx, () -> {
            LabEvalCheck check = checks.findByCompanyIdAndIndicatorCode(caller.companyId(), indicatorCode)
                    .orElseGet(() -> new LabEvalCheck(caller.companyId(), indicatorCode));
            check.fill(status, evidence, aiComment, updatedBy);
            return checks.saveAndFlush(check);
        });
        return toView(saved);
    }

    private static LabEvalCheckDto.View toView(LabEvalCheck c) {
        return new LabEvalCheckDto.View(c.getIndicatorCode(), c.getStatus(), c.getEvidence(), c.getAiComment(),
                c.getUpdatedBy(), c.getUpdatedAt());
    }
}
