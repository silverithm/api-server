package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffTrainingDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffTraining;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffTrainingRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직원 교육·회의 — 법정 교육 이수와 사례회의의 근거 기록. 등록·수정·삭제와 연도별 목록.
 *
 * <p>교육(TRAINING)과 회의(MEETING)는 분류 체계가 달라서 kind와 category가 맞는 조합이어야 저장한다
 * (예: 회의에 "노인학대 예방" 분류를 붙이면 연간 이수 집계가 틀어진다). 참석자는 모두 같은 기관 직원이어야 하고
 * 한 명이라도 아니면 통째로 거절한다.
 */
@Service
public class LabStaffTrainingService {

    static final int MAX_ATTENDEES = 200;
    private static final int CONTENT = 3000;
    private static final int TOPIC = 100;
    private static final int NAME = 50;

    private static final Set<String> KINDS = Set.of("TRAINING", "MEETING");
    private static final Set<String> TRAINING_CATEGORIES = Set.of("ELDER_ABUSE", "SEXUAL_HARASSMENT", "INFECTION",
            "SAFETY", "PRIVACY", "DISABILITY_AWARENESS", "JOB", "OTHER");
    private static final Set<String> MEETING_CATEGORIES = Set.of("CASE", "STAFF", "OTHER");

    private final LabAccessService access;
    private final LabStaffService staff;
    private final LabStaffTrainingRepository trainings;

    public LabStaffTrainingService(LabAccessService access, LabStaffService staff,
                                   LabStaffTrainingRepository trainings) {
        this.access = access;
        this.staff = staff;
        this.trainings = trainings;
    }

    /** 한 해치 — 1월 1일부터 12월 31일까지 양 끝 포함, 날짜 오름차순 */
    @Transactional(readOnly = true)
    public List<LabStaffTrainingDto.View> listByYear(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        List<LabStaffTraining> rows = trainings.findByCompanyIdAndHeldOnBetweenOrderByHeldOnAscIdAsc(
                companyId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));

        // 참석자 이름은 그 해 모든 기록의 id를 모아 한 번에 읽는다
        Set<Long> allIds = new LinkedHashSet<>();
        for (LabStaffTraining t : rows) {
            allIds.addAll(LabJson.readIds(t.getAttendeeIds()));
        }
        Map<Long, String> names = staff.namesOf(allIds, companyId);
        return rows.stream().map(t -> toView(t, names)).toList();
    }

    @Transactional
    public LabStaffTrainingDto.View create(LabStaffTrainingDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabStaffTraining training = new LabStaffTraining(companyId);
        Map<Long, String> names = apply(training, input, companyId);
        return toView(trainings.saveAndFlush(training), names);
    }

    @Transactional
    public LabStaffTrainingDto.View update(Long id, LabStaffTrainingDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabStaffTraining training = trainings.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabStaffTrainingService::notFound);
        Map<Long, String> names = apply(training, input, companyId);
        return toView(trainings.saveAndFlush(training), names);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        trainings.delete(trainings.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabStaffTrainingService::notFound));
    }

    private Map<Long, String> apply(LabStaffTraining training, LabStaffTrainingDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("교육·회의 내용을 보내 주세요");
        }
        String kind = LabValidation.oneOf(input.kind(), KINDS, "구분");
        LocalDate heldOn = LabValidation.required(input.heldOn(), "일시");
        String category = category(kind, input.category());
        String topic = LabValidation.requiredText(input.topic(), TOPIC, "주제");
        String instructor = LabValidation.text(input.instructor(), NAME, "강사·진행자");
        String content = LabValidation.text(input.content(), CONTENT, "내용");

        List<Long> ids = LabValidation.idList(input.attendeeIds(), MAX_ATTENDEES, "참석자");
        Map<Long, String> names = ids.isEmpty() ? Map.of() : staff.requireAllInCompany(ids, companyId);

        training.fill(kind, heldOn, category, topic, instructor, LabJson.write(ids), content,
                Boolean.TRUE.equals(input.aiAssisted()));
        return names;
    }

    /** kind에 맞는 분류인지 — 교육과 회의의 분류 집합이 다르다(회의에 교육 분류를 붙이면 연간 이수 집계가 틀어진다) */
    private static String category(String kind, String value) {
        boolean training = "TRAINING".equals(kind);
        Set<String> allowed = training ? TRAINING_CATEGORIES : MEETING_CATEGORIES;
        String category = LabValidation.text(value, 30, "분류");
        if (category == null || !allowed.contains(category)) {
            throw LabValidation.bad((training ? "교육" : "회의") + "에는 쓸 수 없는 분류입니다. 가능한 분류: "
                    + String.join(", ", allowed.stream().sorted().toList()));
        }
        return category;
    }

    /** 지금도 기관에 있는 직원만 id와 이름을 짝지어 돌려준다 — 퇴사로 지워진 id의 빈 이름이 목록에 뜨지 않게 */
    private static LabStaffTrainingDto.View toView(LabStaffTraining t, Map<Long, String> names) {
        List<Long> ids = new ArrayList<>();
        List<String> attendeeNames = new ArrayList<>();
        for (Long id : LabJson.readIds(t.getAttendeeIds())) {
            String name = names.get(id);
            if (name != null) {
                ids.add(id);
                attendeeNames.add(name);
            }
        }
        return new LabStaffTrainingDto.View(t.getId(), t.getKind(), t.getHeldOn(), t.getCategory(), t.getTopic(),
                t.getInstructor(), ids, attendeeNames, t.getContent(), t.isAiAssisted(), t.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("교육·회의 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
