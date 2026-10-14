package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicationDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicationDoseRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicationRequestRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabMedicationServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 10);
    private static final LocalDate END = LocalDate.of(2026, 1, 20);

    @Autowired private LabMedicationService service;
    @Autowired private LabMedicationRequestRepository requests;
    @Autowired private LabMedicationDoseRepository doses;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        access.companyId = forest.getId();
    }

    private LabMedicationDto.MedicationItem item(String name, String... slots) {
        return new LabMedicationDto.MedicationItem(name, "1정", "ORAL", List.of(slots), "AFTER_MEAL", "물과 함께");
    }

    private LabMedicationDto.RequestInput input(Long elderlyId, List<LabMedicationDto.MedicationItem> medicines,
                                                LocalDate start, LocalDate end, String status) {
        return new LabMedicationDto.RequestInput(elderlyId, START, "김철수", "CHILD", medicines, start, end, true,
                status, "식후 30분", "박간호사", false);
    }

    private LabMedicationDto.RequestView create(String... slots) {
        return service.createRequest(input(kim.getId(), List.of(item("혈압약", slots)), START, END, "ACTIVE"));
    }

    private LabMedicationDto.DoseInput dose(Long requestId, LocalDate day, String slot, String status, String at) {
        return new LabMedicationDto.DoseInput(kim.getId(), requestId, day, slot, status, at, "이요양", "잘 드심");
    }

    @Test
    void requestElderMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        LabMedicationDto.RequestInput foreign = input(stranger.getId(), List.of(item("혈압약", "MORNING")), START, END,
                "ACTIVE");

        assertRejected(() -> service.createRequest(foreign), HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.listRequests(null, stranger.getId()), HttpStatus.NOT_FOUND, null);
        assertThat(requests.count()).isZero();

        LabMedicationDto.RequestView mine = create("MORNING");
        assertRejected(() -> service.updateRequest(mine.id(), foreign), HttpStatus.NOT_FOUND, null);
        access.companyId = other.getId();
        assertRejected(() -> service.deleteRequest(mine.id()), HttpStatus.NOT_FOUND, "투약의뢰서를 찾을 수 없습니다");
        assertRejected(() -> service.upsertDose(dose(mine.id(), START, "MORNING", "GIVEN", "08:30")),
                HttpStatus.NOT_FOUND, "투약의뢰서를 찾을 수 없습니다");
        assertThat(service.listRequests(null, null)).isEmpty();
    }

    @Test
    void medicinesValidated() {
        List<LabMedicationDto.MedicationItem> fifteen = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            fifteen.add(item("약" + i, "MORNING"));
        }
        assertThat(service.createRequest(input(kim.getId(), fifteen, START, END, "ACTIVE")).medicines()).hasSize(15);

        List<LabMedicationDto.MedicationItem> sixteen = new ArrayList<>(fifteen);
        sixteen.add(item("약15", "MORNING"));
        assertRejected(() -> service.createRequest(input(kim.getId(), sixteen, START, END, "ACTIVE")),
                HttpStatus.BAD_REQUEST, "최대 15개");
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(), START, END, "ACTIVE")),
                HttpStatus.BAD_REQUEST, "한 개 이상");
        assertRejected(() -> service.createRequest(input(kim.getId(), null, START, END, "ACTIVE")),
                HttpStatus.BAD_REQUEST, "한 개 이상");
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(item("  ", "MORNING")), START, END,
                "ACTIVE")), HttpStatus.BAD_REQUEST, "약 이름");
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(item("가".repeat(61), "MORNING")),
                START, END, "ACTIVE")), HttpStatus.BAD_REQUEST, "60자");
        // slots 없음·허용 값 밖
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(item("혈압약")), START, END, "ACTIVE")),
                HttpStatus.BAD_REQUEST, "먹는 시간");
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(item("혈압약", "NOON")), START, END,
                "ACTIVE")), HttpStatus.BAD_REQUEST, "먹는 시간");
        // route·timing·dose·note
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(new LabMedicationDto.MedicationItem(
                "혈압약", "1정", "NOSE", List.of("MORNING"), "AFTER_MEAL", null)), START, END, "ACTIVE")),
                HttpStatus.BAD_REQUEST, "투여 경로");
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(new LabMedicationDto.MedicationItem(
                "혈압약", "1정", "ORAL", List.of("MORNING"), "SOMETIME", null)), START, END, "ACTIVE")),
                HttpStatus.BAD_REQUEST, "복용 시점");
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(new LabMedicationDto.MedicationItem(
                "혈압약", "가".repeat(41), "ORAL", List.of("MORNING"), "AFTER_MEAL", null)), START, END, "ACTIVE")),
                HttpStatus.BAD_REQUEST, "40자");
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(new LabMedicationDto.MedicationItem(
                "혈압약", "1정", "ORAL", List.of("MORNING"), "AFTER_MEAL", "가".repeat(101))), START, END, "ACTIVE")),
                HttpStatus.BAD_REQUEST, "100자");
        // 기간·상태·의뢰자
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(item("혈압약", "MORNING")), END, START,
                "ACTIVE")), HttpStatus.BAD_REQUEST, "앞일 수 없습니다");
        assertRejected(() -> service.createRequest(input(kim.getId(), List.of(item("혈압약", "MORNING")), START, END,
                "PAUSED")), HttpStatus.BAD_REQUEST, "상태");
        assertRejected(() -> service.createRequest(new LabMedicationDto.RequestInput(kim.getId(), START, " ", "CHILD",
                List.of(item("혈압약", "MORNING")), START, END, true, "ACTIVE", null, null, false)),
                HttpStatus.BAD_REQUEST, "의뢰자 이름");
        assertRejected(() -> service.createRequest(new LabMedicationDto.RequestInput(kim.getId(), START, "김철수",
                "NEIGHBOR", List.of(item("혈압약", "MORNING")), START, END, true, "ACTIVE", null, null, false)),
                HttpStatus.BAD_REQUEST, "관계");
        assertThat(requests.count()).isEqualTo(1);

        // 시간대는 중복을 없애고 아침→자기 전 순서로 저장·응답한다
        LabMedicationDto.RequestView ordered = service.createRequest(input(kim.getId(),
                List.of(item("혈압약", "BEDTIME", "MORNING", "MORNING", "EVENING")), START, END, null));
        assertThat(ordered.medicines().get(0).slots()).containsExactly("MORNING", "EVENING", "BEDTIME");
        assertThat(ordered.status()).isEqualTo("ACTIVE");
    }

    @Test
    void medicinesEncryptedAtRest() {
        LabMedicationDto.RequestView saved = service.createRequest(new LabMedicationDto.RequestInput(kim.getId(),
                START, "김철수", "CHILD", List.of(item("당뇨약메트포르민", "MORNING", "EVENING")), START, END, true,
                "ACTIVE", "보호자 요청 특이사항", "박간호사", false));
        em.flush();
        em.clear();

        assertEncryptedAtRest(jdbc, "lab_medication_requests", "medicines", "메트포르민");
        assertEncryptedAtRest(jdbc, "lab_medication_requests", "requester_name", "김철수");
        assertEncryptedAtRest(jdbc, "lab_medication_requests", "note", "보호자 요청");

        LabMedicationDto.RequestView read = service.listRequests("ACTIVE", kim.getId()).get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.requesterName()).isEqualTo("김철수");
        assertThat(read.note()).isEqualTo("보호자 요청 특이사항");
        assertThat(read.elderlyName()).isEqualTo("김순자");
        // JSON 필드 이름은 계약 그대로 — 약 한 개는 name, dose, route, slots, timing, note
        LabMedicationDto.MedicationItem m = read.medicines().get(0);
        assertThat(m.name()).isEqualTo("당뇨약메트포르민");
        assertThat(m.dose()).isEqualTo("1정");
        assertThat(m.route()).isEqualTo("ORAL");
        assertThat(m.slots()).containsExactly("MORNING", "EVENING");
        assertThat(m.timing()).isEqualTo("AFTER_MEAL");
        assertThat(m.note()).isEqualTo("물과 함께");

        // 투약 후 상태(observation)도 암호화
        service.upsertDose(dose(saved.id(), START, "MORNING", "GIVEN", "08:30"));
        em.flush();
        assertEncryptedAtRest(jdbc, "lab_medication_doses", "observation", "잘 드심");
    }

    @Test
    void doseOutsideRequestPeriodRejected() {
        LabMedicationDto.RequestView request = create("MORNING");

        assertRejected(() -> service.upsertDose(dose(request.id(), START.minusDays(1), "MORNING", "GIVEN", "08:30")),
                HttpStatus.BAD_REQUEST, "의뢰 기간");
        assertRejected(() -> service.upsertDose(dose(request.id(), END.plusDays(1), "MORNING", "GIVEN", "08:30")),
                HttpStatus.BAD_REQUEST, "의뢰 기간");
        assertThat(doses.count()).isZero();
        // 경계 날짜는 허용
        service.upsertDose(dose(request.id(), START, "MORNING", "GIVEN", "08:30"));
        service.upsertDose(dose(request.id(), END, "MORNING", "GIVEN", "08:30"));
        assertThat(doses.count()).isEqualTo(2);

        // 끝난(ENDED) 의뢰서라도 기간 안 날짜는 허용, 밖은 거절
        LabMedicationDto.RequestView ended = service.updateRequest(request.id(), input(kim.getId(),
                List.of(item("혈압약", "MORNING")), START, END, "ENDED"));
        assertThat(ended.status()).isEqualTo("ENDED");
        assertThat(service.upsertDose(dose(request.id(), START.plusDays(3), "MORNING", "SKIPPED", null)).status())
                .isEqualTo("SKIPPED");
        // 끝나는 날이 없는 의뢰서는 시작일 이후 아무 과거 날짜나 가능하지만 미래는 400
        LabMedicationDto.RequestView open = service.createRequest(input(kim.getId(), List.of(item("눈약", "LUNCH")),
                START, null, "ACTIVE"));
        assertRejected(() -> service.upsertDose(dose(open.id(), LocalDate.of(2099, 1, 1), "LUNCH", "GIVEN", "12:00")),
                HttpStatus.BAD_REQUEST, "오늘보다 뒤");
        assertRejected(() -> service.upsertDose(dose(open.id(), START.minusDays(1), "LUNCH", "GIVEN", "12:00")),
                HttpStatus.BAD_REQUEST, "부터");
        assertThat(service.upsertDose(dose(open.id(), LocalDate.of(2026, 3, 1), "LUNCH", "GIVEN", "12:00")).id())
                .isNotNull();
        // 다른 어르신 id를 섞어 보내면 거절
        Elderly park = LabTestFixtures.elder(em, forest, "박영수");
        assertRejected(() -> service.upsertDose(new LabMedicationDto.DoseInput(park.getId(), open.id(), START, "LUNCH",
                "GIVEN", "12:00", "이요양", null)), HttpStatus.BAD_REQUEST, "어르신");
    }

    @Test
    void doseSlotNotInRequestRejected() {
        LabMedicationDto.RequestView request = create("MORNING", "BEDTIME");

        assertRejected(() -> service.upsertDose(dose(request.id(), START, "LUNCH", "GIVEN", "12:10")),
                HttpStatus.BAD_REQUEST, "없는 시간대");
        assertRejected(() -> service.upsertDose(dose(request.id(), START, "NOON", "GIVEN", "12:10")),
                HttpStatus.BAD_REQUEST, "먹는 시간");
        assertThat(doses.count()).isZero();
        assertThat(service.upsertDose(dose(request.id(), START, "BEDTIME", "GIVEN", "21:00")).slot())
                .isEqualTo("BEDTIME");

        // 이미 기록이 있는 시간대·기간을 의뢰서에서 빼 버리는 수정은 409
        assertRejected(() -> service.updateRequest(request.id(), input(kim.getId(), List.of(item("혈압약", "MORNING")),
                START, END, "ACTIVE")), HttpStatus.CONFLICT, "시간대");
        assertRejected(() -> service.updateRequest(request.id(), input(kim.getId(),
                List.of(item("혈압약", "MORNING", "BEDTIME")), START.plusDays(1), END, "ACTIVE")),
                HttpStatus.CONFLICT, "기간");
        // 기록과 겹치는 범위로는 고칠 수 있다
        assertThat(service.updateRequest(request.id(), input(kim.getId(),
                List.of(item("혈압약", "BEDTIME", "LUNCH")), START, END, "ACTIVE")).medicines().get(0).slots())
                .containsExactly("LUNCH", "BEDTIME");
    }

    @Test
    void doseUpsertByRequestDateSlot() {
        LabMedicationDto.RequestView request = create("MORNING", "LUNCH");

        LabMedicationDto.DoseView first = service.upsertDose(dose(request.id(), START, "MORNING", "GIVEN", "08:30"));
        LabMedicationDto.DoseView again = service.upsertDose(new LabMedicationDto.DoseInput(kim.getId(), request.id(),
                START, "MORNING", "REFUSED", null, "최간호", "삼키기 싫어하심"));

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(doses.count()).isEqualTo(1);
        assertThat(again.status()).isEqualTo("REFUSED");
        assertThat(again.givenAt()).isNull();
        assertThat(again.giverName()).isEqualTo("최간호");
        assertThat(again.observation()).isEqualTo("삼키기 싫어하심");
        assertThat(again.elderlyId()).isEqualTo(kim.getId());
        assertThat(again.elderlyName()).isEqualTo("김순자");

        // 다른 시간대·다른 날은 새 기록
        service.upsertDose(dose(request.id(), START, "LUNCH", "GIVEN", "12:10"));
        service.upsertDose(dose(request.id(), START.plusDays(1), "MORNING", "GIVEN", "08:40"));
        assertThat(doses.count()).isEqualTo(3);
        assertThat(service.listDoses("2026-01-10", null, null)).extracting(LabMedicationDto.DoseView::slot)
                .containsExactly("MORNING", "LUNCH");
        assertThat(service.listDoses(null, kim.getId(), "2026-01")).hasSize(3);
        assertThat(service.listDoses(null, kim.getId(), "2026-02")).isEmpty();
        assertRejected(() -> service.listDoses(null, null, null), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.listDoses(null, kim.getId(), null), HttpStatus.BAD_REQUEST, null);

        service.deleteDose(first.id());
        assertThat(doses.count()).isEqualTo(2);
        assertRejected(() -> service.deleteDose(first.id()), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void givenRequiresTime() {
        LabMedicationDto.RequestView request = create("MORNING");

        assertRejected(() -> service.upsertDose(dose(request.id(), START, "MORNING", "GIVEN", null)),
                HttpStatus.BAD_REQUEST, "투약 시각");
        assertRejected(() -> service.upsertDose(dose(request.id(), START, "MORNING", "GIVEN", " ")),
                HttpStatus.BAD_REQUEST, "투약 시각");
        assertRejected(() -> service.upsertDose(dose(request.id(), START, "MORNING", "GIVEN", "8:30")),
                HttpStatus.BAD_REQUEST, "HH:MM");
        assertRejected(() -> service.upsertDose(dose(request.id(), START, "MORNING", "GIVEN", "25:00")),
                HttpStatus.BAD_REQUEST, "HH:MM");
        assertRejected(() -> service.upsertDose(dose(request.id(), START, "MORNING", "TAKEN", "08:30")),
                HttpStatus.BAD_REQUEST, "투약 상태");
        assertRejected(() -> service.upsertDose(new LabMedicationDto.DoseInput(kim.getId(), request.id(), START,
                "MORNING", "GIVEN", "08:30", " ", null)), HttpStatus.BAD_REQUEST, "이름");
        assertThat(doses.count()).isZero();
        // 안 먹인 기록(SKIPPED·REFUSED)은 시각이 없어도 되고, 보내도 저장하지 않는다
        assertThat(service.upsertDose(dose(request.id(), START, "MORNING", "SKIPPED", null)).givenAt()).isNull();
        assertThat(service.upsertDose(dose(request.id(), START, "MORNING", "REFUSED", "08:30")).givenAt()).isNull();
        assertThat(service.upsertDose(dose(request.id(), START, "MORNING", "GIVEN", "08:30")).givenAt())
                .isEqualTo("08:30");
    }

    @Test
    void deleteRequestWithDosesRejected() {
        LabMedicationDto.RequestView withDose = create("MORNING");
        LabMedicationDto.DoseView recorded = service.upsertDose(dose(withDose.id(), START, "MORNING", "GIVEN", "08:30"));

        assertRejected(() -> service.deleteRequest(withDose.id()), HttpStatus.CONFLICT, "투약 기록이 있는");
        assertThat(requests.count()).isEqualTo(1);
        assertThat(doses.count()).isEqualTo(1);
        // 투약 기록이 있으면 어르신을 바꾸는 수정도 막는다
        Elderly park = LabTestFixtures.elder(em, forest, "박영수");
        assertRejected(() -> service.updateRequest(withDose.id(), input(park.getId(),
                List.of(item("혈압약", "MORNING")), START, END, "ACTIVE")), HttpStatus.CONFLICT, "어르신");

        // 기록을 지우면 의뢰서도 지울 수 있다. 기록이 없던 의뢰서는 바로 지워진다
        service.deleteDose(recorded.id());
        service.deleteRequest(withDose.id());
        LabMedicationDto.RequestView empty = create("LUNCH");
        service.deleteRequest(empty.id());
        assertThat(requests.count()).isZero();
    }
}
