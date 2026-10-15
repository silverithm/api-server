package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabOutbreakDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabOutbreak;
import com.silverithm.vehicleplacementsystem.lab.repository.LabOutbreakRepository;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감염병 유행·발생·국가 위기 경보 기록 — 종류·병명·시작일·환자 수·신고 일자·조치·종료일.
 *
 * <p>모든 날짜는 오늘 이후일 수 없고, 보호자 안내·보건소 신고·지자체·공단 보고·종료일은 시작일보다 앞설 수 없다.
 * 국가 위기 경보(NATIONAL)만 경보 단계가 필수이고 그 밖의 종류에서는 비운다. 조치는 정한 키만, 중복 없이 받는다.
 * 신고가 빠졌는지·진행 중인지의 판정은 웹이 한다.
 */
@Service
public class LabOutbreakService {

    static final Set<String> KINDS = Set.of("OUTBREAK", "CASE", "NATIONAL");
    static final Set<String> ALERT_LEVELS = Set.of("BLUE", "YELLOW", "ORANGE", "RED");
    /** 조치 키 — 웹 OUTBREAK_ACTIONS와 같은 순서(저장도 이 순서로 정리한다) */
    static final List<String> ACTIONS = List.of("VISIT_LIMIT", "FEVER_CHECK", "EXTRA_DISINFECT", "HYGIENE_SUPPLY",
            "EXTRA_TRAINING", "FLU_VACCINE", "MASK_HAND", "SYMPTOM_WATCH", "ISOLATION", "HOSPITAL_TRANSFER",
            "ACTIVITY_STOP");
    private static final int MAX_CASES = 500;
    private static final int NOTE = 1000;

    private final LabAccessService access;
    private final LabOutbreakRepository outbreaks;

    public LabOutbreakService(LabAccessService access, LabOutbreakRepository outbreaks) {
        this.access = access;
        this.outbreaks = outbreaks;
    }

    @Transactional(readOnly = true)
    public List<LabOutbreakDto.View> list(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        return outbreaks.findByCompanyIdAndStartedOnBetweenOrderByStartedOnDescIdDesc(
                companyId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)).stream().map(LabOutbreakService::view)
                .toList();
    }

    @Transactional
    public LabOutbreakDto.View create(LabOutbreakDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        LabOutbreak row = new LabOutbreak(caller.companyId());
        apply(row, input, caller);
        return view(outbreaks.saveAndFlush(row));
    }

    @Transactional
    public LabOutbreakDto.View update(Long id, LabOutbreakDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        LabOutbreak row = outbreaks.findByIdAndCompanyId(id, caller.companyId())
                .orElseThrow(LabOutbreakService::notFound);
        apply(row, input, caller);
        return view(outbreaks.saveAndFlush(row));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        outbreaks.delete(outbreaks.findByIdAndCompanyId(id, companyId).orElseThrow(LabOutbreakService::notFound));
    }

    private void apply(LabOutbreak row, LabOutbreakDto.Input input, LabAccessService.Caller caller) {
        if (input == null) {
            throw LabValidation.bad("감염병 기록 내용을 보내 주세요");
        }
        String kind = LabValidation.oneOf(input.kind(), KINDS, "종류");
        String disease = LabValidation.requiredText(input.disease(), 50, "병명·증상");
        LocalDate startedOn = LabValidation.pastOrToday(input.startedOn(), "시작일");
        int elderCases = cases(input.elderCases(), "수급자 환자 수");
        int staffCases = cases(input.staffCases(), "직원 환자 수");
        LocalDate guardian = notBefore(input.guardianNotifiedOn(), startedOn, "보호자 안내일", "시작일");
        LocalDate healthCenter = notBefore(input.healthCenterReportedOn(), startedOn, "보건소 신고일", "시작일");
        LocalDate authority = notBefore(input.authorityReportedOn(), startedOn, "지자체·공단 보고일", "시작일");
        LocalDate endedOn = notBefore(input.endedOn(), startedOn, "종료일", "시작일");

        String level = input.alertLevel() == null || input.alertLevel().isBlank() ? null
                : LabValidation.oneOf(input.alertLevel(), ALERT_LEVELS, "경보 단계");
        if ("NATIONAL".equals(kind)) {
            if (level == null) {
                throw LabValidation.bad("국가 위기 경보는 경보 단계(관심·주의·경계·심각)를 선택해 주세요");
            }
        } else {
            level = null;
        }
        List<String> actions = actions(input.actions());
        String note = LabValidation.text(input.note(), NOTE, "메모");

        row.fill(kind, disease, startedOn, (short) elderCases, (short) staffCases, guardian, healthCenter,
                authority, level, LabJson.write(actions), endedOn, note, access.displayName(caller));
    }

    private static int cases(Integer value, String label) {
        return (int) LabValidation.longInRange(value == null ? Long.valueOf(0) : Long.valueOf(value), 0, MAX_CASES,
                label);
    }

    /** 비었으면 null, 있으면 오늘 이후가 아니고 기준일 이상이어야 한다 */
    private static LocalDate notBefore(LocalDate value, LocalDate base, String label, String baseLabel) {
        LocalDate date = LabValidation.pastOrTodayOrNull(value, label);
        if (date != null && date.isBefore(base)) {
            throw LabValidation.bad(label + "은(는) " + baseLabel + "보다 앞설 수 없습니다");
        }
        return date;
    }

    /** 정한 키만, 중복 없이 — 저장은 정해진 순서로 */
    static List<String> actions(List<String> input) {
        if (input == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : input) {
            String key = raw == null ? null : raw.strip();
            if (key == null || !ACTIONS.contains(key)) {
                throw LabValidation.bad("알 수 없는 조치가 들어 있습니다");
            }
            if (!seen.add(key)) {
                throw LabValidation.bad("같은 조치가 두 번 들어 있습니다");
            }
        }
        return ACTIONS.stream().filter(seen::contains).toList();
    }

    private static LabOutbreakDto.View view(LabOutbreak o) {
        return new LabOutbreakDto.View(o.getId(), o.getKind(), o.getDisease(), o.getStartedOn(), o.getElderCases(),
                o.getStaffCases(), o.getGuardianNotifiedOn(), o.getHealthCenterReportedOn(),
                o.getAuthorityReportedOn(), o.getAlertLevel(), LabJson.readStrings(o.getActions()), o.getEndedOn(),
                o.getNote(), o.getWriterName(), o.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("감염병 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
