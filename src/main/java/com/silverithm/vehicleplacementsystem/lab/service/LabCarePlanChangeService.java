package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCarePlanChangeDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCareEvent;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCarePlanChange;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCareEventRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCarePlanChangeRepository;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 급여제공계획 변경 이력 — 변경 사유·항목·내용·관련 상태변화·보호자 설명·서명·공단 통보.
 *
 * <p>어르신·상태변화 id는 클라이언트가 보내는 값이라 요청자 기관 소속인지 확인하고, 상태변화는 같은 어르신 것이어야 한다.
 * 아니면 존재 여부를 알려 주지 않도록 404다. 변경일은 오늘 이후일 수 없고, 설명·서명·통보일은 변경일보다 앞설 수 없다.
 * 변경 항목(CONTENT·TIME·FREQUENCY)은 1~3개, 중복 없이. 변경 내용은 암호화한다.
 */
@Service
public class LabCarePlanChangeService {

    static final List<String> ITEMS = List.of("CONTENT", "TIME", "FREQUENCY");
    static final Set<String> REASONS = Set.of("FUNCTION_CHANGE", "ELDER_WISH", "GUARDIAN_REQUEST", "GRADE_CHANGE",
            "OTHER");
    private static final int REASON_NOTE = 300;
    private static final int DETAIL = 1000;

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabCarePlanChangeRepository changes;
    private final LabCareEventRepository events;

    public LabCarePlanChangeService(LabAccessService access, LabElderDirectory elders,
                                    LabCarePlanChangeRepository changes, LabCareEventRepository events) {
        this.access = access;
        this.elders = elders;
        this.changes = changes;
        this.events = events;
    }

    /** elderlyId 또는 year(또는 둘 다)로 거른다 — 둘 다 없으면 400 */
    @Transactional(readOnly = true)
    public List<LabCarePlanChangeDto.View> list(Long elderlyId, String year) {
        Long companyId = access.requireCompanyId();
        boolean hasYear = year != null && !year.isBlank();
        if (elderlyId == null && !hasYear) {
            throw LabValidation.bad("elderlyId 또는 year 중 하나를 보내 주세요");
        }
        List<LabCarePlanChange> rows;
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
            rows = changes.findByCompanyIdAndElderlyIdOrderByChangedOnDescIdDesc(companyId, elderlyId);
            if (hasYear) {
                int y = LabValidation.year(year, "연도");
                rows = rows.stream().filter(c -> c.getChangedOn().getYear() == y).toList();
            }
        } else {
            int y = LabValidation.year(year, "연도");
            rows = changes.findByCompanyIdAndChangedOnBetweenOrderByChangedOnDescIdDesc(companyId,
                    LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        }
        Map<Long, String> names = rows.isEmpty() ? Map.of() : elders.namesInCompany(companyId);
        return rows.stream().map(c -> view(c, names.getOrDefault(c.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabCarePlanChangeDto.View create(LabCarePlanChangeDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        LabCarePlanChange row = new LabCarePlanChange(caller.companyId());
        apply(row, input, caller);
        LabCarePlanChange saved = changes.saveAndFlush(row);
        return view(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public LabCarePlanChangeDto.View update(Long id, LabCarePlanChangeDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        LabCarePlanChange row = changes.findByIdAndCompanyId(id, caller.companyId())
                .orElseThrow(LabCarePlanChangeService::notFound);
        apply(row, input, caller);
        LabCarePlanChange saved = changes.saveAndFlush(row);
        return view(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        changes.delete(changes.findByIdAndCompanyId(id, companyId).orElseThrow(LabCarePlanChangeService::notFound));
    }

    private void apply(LabCarePlanChange row, LabCarePlanChangeDto.Input input, LabAccessService.Caller caller) {
        Long companyId = caller.companyId();
        if (input == null) {
            throw LabValidation.bad("계획 변경 내용을 보내 주세요");
        }
        if (input.elderlyId() == null) {
            throw LabValidation.bad("어르신을 선택해 주세요");
        }
        LocalDate changedOn = LabValidation.pastOrToday(input.changedOn(), "변경일");
        String reason = LabValidation.oneOf(input.reason(), REASONS, "변경 사유");
        String reasonNote = LabValidation.requiredText(input.reasonNote(), REASON_NOTE, "변경 사유 설명");
        List<String> items = items(input.changeItems());
        String detail = LabValidation.requiredText(input.changeDetail(), DETAIL, "변경 내용");
        LocalDate explained = notBefore(input.guardianExplainedOn(), changedOn, "보호자 설명일");
        LocalDate signed = notBefore(input.guardianSignedOn(), changedOn, "보호자 서명일");
        LocalDate notified = notBefore(input.notifiedOn(), changedOn, "공단 통보일");

        // 입력 모양이 맞은 뒤에 기관·같은 어르신 소속을 확인한다
        elders.requireInCompany(input.elderlyId(), companyId);
        if (input.eventId() != null) {
            LabCareEvent event = events.findByIdAndCompanyId(input.eventId(), companyId)
                    .orElseThrow(LabCarePlanChangeService::eventNotFound);
            if (!input.elderlyId().equals(event.getElderlyId())) {
                throw eventNotFound(); // 같은 기관이어도 다른 어르신의 사건이면 없는 것과 같다
            }
        }

        row.fill(input.elderlyId(), changedOn, reason, reasonNote, LabJson.write(items), detail, input.eventId(),
                explained, signed, notified, access.displayName(caller));
    }

    /** 변경 항목 1~3개, 정한 키만, 중복 없이 — 저장은 정해진 순서로 */
    static List<String> items(List<String> input) {
        if (input == null || input.isEmpty()) {
            throw LabValidation.bad("변경 항목(내용·시간·횟수)을 1개 이상 선택해 주세요");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : input) {
            String key = raw == null ? null : raw.strip();
            if (key == null || !ITEMS.contains(key)) {
                throw LabValidation.bad("알 수 없는 변경 항목이 들어 있습니다");
            }
            if (!seen.add(key)) {
                throw LabValidation.bad("같은 변경 항목이 두 번 들어 있습니다");
            }
        }
        return ITEMS.stream().filter(seen::contains).toList();
    }

    private static LocalDate notBefore(LocalDate value, LocalDate changedOn, String label) {
        LocalDate date = LabValidation.pastOrTodayOrNull(value, label);
        if (date != null && date.isBefore(changedOn)) {
            throw LabValidation.bad(label + "은(는) 변경일보다 앞설 수 없습니다");
        }
        return date;
    }

    private static LabCarePlanChangeDto.View view(LabCarePlanChange c, String elderlyName) {
        return new LabCarePlanChangeDto.View(c.getId(), c.getElderlyId(), elderlyName, c.getChangedOn(),
                c.getReason(), c.getReasonNote(), LabJson.readStrings(c.getChangeItems()), c.getChangeDetail(),
                c.getEventId(), c.getGuardianExplainedOn(), c.getGuardianSignedOn(), c.getNotifiedOn(),
                c.getWriterName(), c.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("계획 변경 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }

    private static CustomException eventNotFound() {
        return new CustomException("상태변화 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
