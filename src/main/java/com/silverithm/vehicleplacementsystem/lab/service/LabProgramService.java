package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabProgram;
import com.silverithm.vehicleplacementsystem.lab.repository.LabProgramRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로그램 일지 — 하루 활동 1건에 어르신 여러 명이 참여한다. 등록·수정·삭제와 월별 목록.
 *
 * <p>참여자 id는 클라이언트가 보내는 값이라, 하나라도 다른 기관 어르신이면 통째로 거절한다(404).
 * 응답의 참여자 이름은 서버가 채우며, 목록에서도 기관 어르신 이름을 한 번만 읽어 모든 프로그램에 나눠 쓴다.
 */
@Service
public class LabProgramService {

    static final int MAX_PARTICIPANTS = 100;
    private static final int LONG_TEXT = 2000;
    private static final int TITLE = 100;
    private static final int NAME = 50;
    private static final Set<String> CATEGORIES = Set.of("COGNITIVE", "PHYSICAL", "LEISURE", "SOCIAL", "OTHER");
    private static final Pattern START_TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabProgramRepository programs;

    public LabProgramService(LabAccessService access, LabElderDirectory elders, LabProgramRepository programs) {
        this.access = access;
        this.elders = elders;
        this.programs = programs;
    }

    /** 한 달치 — 날짜순, 같은 날은 시작 시각순. 1일부터 말일까지 양 끝을 포함한다. */
    @Transactional(readOnly = true)
    public List<LabProgramDto.View> listByMonth(String month) {
        Long companyId = access.requireCompanyId();
        YearMonth ym = LabValidation.yearMonth(month, "월");
        Map<Long, String> names = elders.namesInCompany(companyId);
        return programs.findByCompanyIdAndProgramDateBetweenOrderByProgramDateAscStartTimeAscIdAsc(
                        companyId, ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(p -> toView(p, names))
                .toList();
    }

    @Transactional
    public LabProgramDto.View create(LabProgramDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabProgram program = new LabProgram(companyId);
        Map<Long, String> names = apply(program, input, companyId);
        return toView(programs.saveAndFlush(program), names);
    }

    @Transactional
    public LabProgramDto.View update(Long id, LabProgramDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabProgram program = programs.findByIdAndCompanyId(id, companyId).orElseThrow(LabProgramService::notFound);
        Map<Long, String> names = apply(program, input, companyId);
        return toView(programs.saveAndFlush(program), names);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        LabProgram program = programs.findByIdAndCompanyId(id, companyId).orElseThrow(LabProgramService::notFound);
        programs.delete(program);
    }

    /** 입력을 검증해 엔티티에 채우고, 확인된 참여자 이름표를 돌려준다(응답에서 한 번 더 읽지 않으려고) */
    private Map<Long, String> apply(LabProgram program, LabProgramDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("프로그램 내용을 보내 주세요");
        }
        LocalDate date = LabValidation.required(input.programDate(), "프로그램 날짜");
        String startTime = startTime(input.startTime());
        String category = LabValidation.oneOf(input.category(), CATEGORIES, "프로그램 분류");
        String title = LabValidation.requiredText(input.title(), TITLE, "프로그램 이름");
        String goal = LabValidation.text(input.goal(), LONG_TEXT, "목표");
        String content = LabValidation.text(input.content(), LONG_TEXT, "진행 내용");
        String reaction = LabValidation.text(input.reaction(), LONG_TEXT, "참여자 반응");
        String instructor = LabValidation.text(input.instructorName(), NAME, "진행자 이름");

        List<Long> ids = LabValidation.idList(input.participantIds(), MAX_PARTICIPANTS, "참여자");
        // 한 번의 조회로 전원이 이 기관 어르신인지 확인한다 — 한 명이라도 아니면 아무것도 저장하지 않는다
        Map<Long, String> names = ids.isEmpty() ? Map.of() : elders.requireAllInCompany(ids, companyId);

        program.fill(date, startTime, category, title, goal, content, reaction, instructor,
                LabJson.write(ids), Boolean.TRUE.equals(input.aiAssisted()));
        return names;
    }

    private static String startTime(String value) {
        String text = LabValidation.text(value, 5, "시작 시각");
        if (text == null) {
            return null;
        }
        if (!START_TIME.matcher(text).matches()) {
            throw LabValidation.bad("시작 시각은 HH:MM 형식(예: 14:30)이어야 합니다");
        }
        return text;
    }

    /**
     * 저장된 참여자 중 지금도 기관에 있는 어르신만 id와 이름을 짝지어 돌려준다.
     * 프로그램에는 어르신 FK가 없어, 어르신이 삭제되면 id만 남는다 — 이름 없는 빈칸이 목록에 뜨지 않게 거른다.
     */
    private static LabProgramDto.View toView(LabProgram p, Map<Long, String> names) {
        List<Long> ids = new ArrayList<>();
        List<String> participantNames = new ArrayList<>();
        for (Long id : LabJson.readIds(p.getParticipantIds())) {
            String name = names.get(id);
            if (name != null) {
                ids.add(id);
                participantNames.add(name);
            }
        }
        return new LabProgramDto.View(p.getId(), p.getProgramDate(), p.getStartTime(), p.getCategory(),
                p.getTitle(), p.getGoal(), p.getContent(), p.getReaction(), p.getInstructorName(), ids,
                participantNames, p.isAiAssisted(), p.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("프로그램 일지를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
