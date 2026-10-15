package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareRecordDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCareRecordRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabCareRecordServiceTest {

    @Autowired private LabCareRecordService service;
    @Autowired private LabCareRecordRepository records;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        access.companyId = forest.getId();
    }

    private LabCareRecordDto.Input input(Long elderlyId, LocalDate date, String physical, String rawNote) {
        return new LabCareRecordDto.Input(elderlyId, date, physical, null, "죽 1/2 섭취", null, null, null, null,
                "혈압 140/90, 어지러움 호소", rawNote, "박요양", true, null);
    }

    // 같은 어르신·같은 날을 다시 저장하면 기록이 늘지 않고 내용만 바뀐다
    @Test
    void upsertKeepsOneRecordPerElderAndDate() {
        LabCareRecordDto.View first = service.upsert(input(kim.getId(), DAY, "보행 부축", null));
        LabCareRecordDto.View second = service.upsert(input(kim.getId(), DAY, "휠체어 이동 보조", null));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(records.count()).isEqualTo(1);
        assertThat(second.physical()).isEqualTo("휠체어 이동 보조");
        assertThat(second.elderlyName()).isEqualTo("김순자");
        assertThat(second.aiAssisted()).isTrue();

        service.upsert(input(kim.getId(), DAY.plusDays(1), "보행 부축", null));
        assertThat(records.count()).isEqualTo(2);
        assertThat(service.listByDate("2026-10-14")).hasSize(1);
        assertThat(service.listByElderAndMonth(kim.getId(), "2026-10"))
                .extracting(LabCareRecordDto.View::recordDate).containsExactly(DAY, DAY.plusDays(1));
        assertThat(service.listByElderAndMonth(kim.getId(), "2026-11")).isEmpty();
    }

    // 다른 기관의 어르신 ID로는 저장도 조회도 404
    @Test
    void otherCompanyElderRejected() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");

        assertThatThrownBy(() -> service.upsert(input(stranger.getId(), DAY, "x", null)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("어르신을 찾을 수 없습니다");
                });
        assertThatThrownBy(() -> service.listByElderAndMonth(stranger.getId(), "2026-10"))
                .isInstanceOf(CustomException.class);
        assertThat(records.count()).isZero();
    }

    // 돌봄 문장은 DB에 암호문으로 들어가고, 읽을 때만 평문으로 돌아온다
    @Test
    void notesAreEncryptedAtRest() {
        String plain = "김순자 어르신 오전에 혈압 140/90 어지러움 호소";
        service.upsert(input(kim.getId(), DAY, "보행 부축", plain));
        em.flush();
        em.clear();

        // 엔티티가 아니라 DB에 실제로 들어간 값을 본다 — 컨버터가 빠지면 평문이 그대로 저장되기 때문이다
        for (String column : List.of("raw_note", "special_note", "meal", "physical")) {
            String stored = jdbc.queryForObject("SELECT " + column + " FROM lab_care_records", String.class);
            assertThat(stored).as(column).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        }
        String rawStored = jdbc.queryForObject("SELECT raw_note FROM lab_care_records", String.class);
        assertThat(rawStored).doesNotContain("혈압").isNotEqualTo(plain);

        assertThat(service.listByDate("2026-10-14").get(0).rawNote()).isEqualTo(plain);
    }

    // 글자 수 제한을 넘기거나 날짜가 없으면 400
    @Test
    void validatesInput() {
        String tooLong = "가".repeat(2001);
        assertThatThrownBy(() -> service.upsert(input(kim.getId(), DAY, tooLong, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.upsert(new LabCareRecordDto.Input(kim.getId(), DAY, null, null,
                "가".repeat(201), null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.upsert(input(kim.getId(), null, "x", null)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.listByDate("2026-13-40")).isInstanceOf(CustomException.class);
    }

    // 삭제는 자기 기관의 기록만 지운다
    @Test
    void deleteIsScopedToCompany() {
        LabCareRecordDto.View saved = service.upsert(input(kim.getId(), DAY, "x", null));
        access.companyId = forest.getId() + 1000;

        assertThatThrownBy(() -> service.delete(saved.id())).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(records.count()).isEqualTo(1);

        access.companyId = forest.getId();
        service.delete(saved.id());
        assertThat(records.count()).isZero();
    }

    // ---- 12차: 급여제공기록지 법정 서식 칸(form) ----

    private static LabCareRecordDto.Form form(String start, String end, Integer systolic, Integer diastolic,
                                              BigDecimal temperature, Map<String, Long> writers,
                                              List<LabCareRecordDto.Reason> reasons) {
        return new LabCareRecordDto.Form(start, end, true, "12가3456", true, 20, "SHOWER", "PORRIDGE",
                "HALF_OR_MORE", 3, true, true, false, systolic, diastolic, temperature, 10, 5, false,
                "회상 훈련", false, true, false, false, writers, null, reasons);
    }

    private LabCareRecordDto.Input withForm(LabCareRecordDto.Form form) {
        return new LabCareRecordDto.Input(kim.getId(), DAY, "보행 부축", null, null, null, null, null, null, null,
                null, null, false, form);
    }

    private Member staff(Company company, String name) {
        return LabTestFixtures.member(em, company, name, Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
    }

    private static void assertBad(Runnable call, String message) {
        LabTestAsserts.assertRejected(call, HttpStatus.BAD_REQUEST, message);
    }

    // 서식 칸이 저장·조회로 그대로 돌아오고, 제공 직원 이름이 붙고, DB에는 암호문으로 들어간다
    @Test
    void formRoundTrip() {
        Member lee = staff(forest, "이요양");
        Member nurse = staff(forest, "한간호");
        Map<String, Long> writers = new LinkedHashMap<>();
        writers.put("PHYSICAL", lee.getId());
        writers.put("HEALTH", nurse.getId());
        writers.put("HEALTH_EXTRA", 999L); // 모르는 영역 키는 조용히 버려진다
        List<LabCareRecordDto.Reason> reasons = List.of(
                new LabCareRecordDto.Reason("AFTER_MIDNIGHT", " 보호자 요청으로 연장 "),
                new LabCareRecordDto.Reason("OVER_13H", "야간 돌봄"));

        LabCareRecordDto.View saved = service.upsert(withForm(
                form("08:30", "19:00", 128, 82, new BigDecimal("36.5"), writers, reasons)));

        LabCareRecordDto.Form form = saved.form();
        assertThat(form.startTime()).isEqualTo("08:30");
        assertThat(form.endTime()).isEqualTo("19:00");
        assertThat(form.transport()).isTrue();
        assertThat(form.vehicleNo()).isEqualTo("12가3456");
        assertThat(form.bathMethod()).isEqualTo("SHOWER");
        assertThat(form.mealKind()).isEqualTo("PORRIDGE");
        assertThat(form.mealIntake()).isEqualTo("HALF_OR_MORE");
        assertThat(form.bpSystolic()).isEqualTo(128);
        assertThat(form.bpDiastolic()).isEqualTo(82);
        assertThat(form.temperature()).isEqualByComparingTo("36.5");
        assertThat(form.rehabCogActivity()).isTrue();
        assertThat(form.emergency()).isFalse();
        assertThat(form.writers()).containsOnlyKeys("PHYSICAL", "COGNITIVE", "HEALTH", "REHAB")
                .containsEntry("PHYSICAL", lee.getId()).containsEntry("HEALTH", nurse.getId())
                .containsEntry("COGNITIVE", null);
        assertThat(form.writerNames()).containsEntry("PHYSICAL", "이요양").containsEntry("HEALTH", "한간호")
                .containsEntry("REHAB", null);
        assertThat(form.reasons()).extracting(LabCareRecordDto.Reason::text).containsExactly("보호자 요청으로 연장", "야간 돌봄");

        em.flush();
        em.clear();
        // 응답용 이름은 저장 JSON에 남지 않고, 열 전체가 암호문이다
        String stored = jdbc.queryForObject("SELECT form FROM lab_care_records", String.class);
        assertThat(stored).startsWith(EncryptedPiiConverter.ENC_PREFIX).doesNotContain("12가3456").doesNotContain("36.5");
        LabTestAsserts.assertEncryptedAtRest(jdbc, "lab_care_records", "form", "이요양");

        LabCareRecordDto.Form again = service.listByDate("2026-10-14").get(0).form();
        assertThat(again.writerNames()).containsEntry("PHYSICAL", "이요양");
        assertThat(again.temperature()).isEqualByComparingTo("36.5");
        assertThat(service.listByElderAndMonth(kim.getId(), "2026-10").get(0).form().bpSystolic()).isEqualTo(128);

        // 같은 날 다시 저장하면 form도 통째로 바뀐다 — form을 비우면 기존 값을 유지하지 않고 null이 된다
        assertThat(service.upsert(withForm(null)).form()).isNull();
        assertThat(jdbc.queryForObject("SELECT form FROM lab_care_records", String.class)).isNull();
    }

    // 다른 기관 직원을 제공 직원으로 넣으면 404, 아무것도 저장되지 않는다
    @Test
    void formWriterMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Member stranger = staff(other, "외부직원");
        Member mine = staff(forest, "이요양");

        for (Map<String, Long> writers : List.of(
                Map.of("PHYSICAL", stranger.getId()),
                Map.of("PHYSICAL", mine.getId(), "REHAB", stranger.getId()),
                Map.of("COGNITIVE", 999_999L))) {
            LabTestAsserts.assertRejected(() -> service.upsert(withForm(
                    form(null, null, null, null, null, writers, null))), HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        }
        assertThat(records.count()).isZero();

        // 같은 기관 직원이면 통과
        assertThat(service.upsert(withForm(form(null, null, null, null, null,
                Map.of("PHYSICAL", mine.getId()), null))).form().writerNames()).containsEntry("PHYSICAL", "이요양");
    }

    // 혈압은 둘 다 있거나 둘 다 없어야 한다. 범위·체온 자릿수도 400
    @Test
    void formBpPairRequired() {
        assertBad(() -> service.upsert(withForm(form(null, null, 120, null, null, null, null))), "혈압");
        assertBad(() -> service.upsert(withForm(form(null, null, null, 80, null, null, null))), "혈압");
        assertBad(() -> service.upsert(withForm(form(null, null, 261, 80, null, null, null))), "수축기");
        assertBad(() -> service.upsert(withForm(form(null, null, 120, 29, null, null, null))), "이완기");
        assertBad(() -> service.upsert(withForm(form(null, null, null, null, new BigDecimal("36.55"), null, null))),
                "소수 첫째");
        assertBad(() -> service.upsert(withForm(form(null, null, null, null, new BigDecimal("42.1"), null, null))),
                "34.0~42.0");
        assertBad(() -> service.upsert(withForm(form("8:30", null, null, null, null, null, null))), "HH:MM");
        assertThat(records.count()).isZero();

        // 둘 다 없거나 둘 다 있으면 통과, 시작만 있는 시각도 저장된다(경고는 웹에서)
        assertThat(service.upsert(withForm(form("09:00", null, null, null, null, null, null))).form().bpSystolic())
                .isNull();
        assertThat(service.upsert(withForm(form(null, null, 50, 30, new BigDecimal("34"), null, null))).form()
                .temperature()).isEqualByComparingTo("34.0");
    }

    // 같은 종류의 사유가 두 번이거나 내용이 비었거나 모르는 종류면 400
    @Test
    void formDuplicateReasonRejected() {
        assertBad(() -> service.upsert(withForm(form(null, null, null, null, null, null, List.of(
                new LabCareRecordDto.Reason("UNUSED_DAY", "입원"),
                new LabCareRecordDto.Reason("UNUSED_DAY", "외출"))))), "두 번");
        assertBad(() -> service.upsert(withForm(form(null, null, null, null, null, null, List.of(
                new LabCareRecordDto.Reason("UNUSED_DAY", "  "))))), "사유 내용");
        assertBad(() -> service.upsert(withForm(form(null, null, null, null, null, null, List.of(
                new LabCareRecordDto.Reason("UNUSED_DAY", "가".repeat(301)))))), "300자");
        assertBad(() -> service.upsert(withForm(form(null, null, null, null, null, null, List.of(
                new LabCareRecordDto.Reason("OTHER_REASON", "x"))))), "사유 종류");
        assertThat(records.count()).isZero();

        assertThat(service.upsert(withForm(form(null, null, null, null, null, null, List.of(
                new LabCareRecordDto.Reason("UNUSED_DAY", "입원"),
                new LabCareRecordDto.Reason("COG_PROGRAM_MISSED", "거부"))))).form().reasons()).hasSize(2);
    }

    // form이 없는 옛 기록(열이 NULL)은 그대로 읽히고, form 없이 저장해도 된다
    @Test
    void oldRecordWithoutFormStillRead() {
        service.upsert(withForm(form("09:00", "17:00", null, null, null, null, null)));
        jdbc.update("UPDATE lab_care_records SET form = NULL");
        em.clear();

        List<LabCareRecordDto.View> day = service.listByDate("2026-10-14");
        assertThat(day).hasSize(1);
        assertThat(day.get(0).form()).isNull();
        assertThat(day.get(0).physical()).isEqualTo("보행 부축");
        assertThat(service.listByElderAndMonth(kim.getId(), "2026-10").get(0).form()).isNull();

        // 깨진 값이 들어 있어도 목록 전체가 죽지 않고 form만 비어 보인다
        jdbc.update("UPDATE lab_care_records SET form = '{깨진'");
        em.clear();
        assertThat(service.listByDate("2026-10-14")).hasSize(1).allSatisfy(v -> assertThat(v.form()).isNull());

        // form 없이 저장해도 된다
        assertThat(service.upsert(input(kim.getId(), DAY.plusDays(1), "x", null)).form()).isNull();
    }
}
