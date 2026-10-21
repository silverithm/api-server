package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabRecordLogDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabRecordLogRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** 종류별 기록(업무수행일지·시설운영일지·안전관리 설명·현금영수증) — 종류마다 필수 칸과 허용 항목이 다른지 */
@LabJpaTest
class LabRecordLogServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Autowired private LabRecordLogService service;
    @Autowired private LabRecordLogRepository logs;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김어르신");
        access.companyId = forest.getId();
    }

    private LabRecordLogDto.Input duty(String kind, LocalDate date, List<String> checks, String body) {
        return new LabRecordLogDto.Input(kind, date, null, "이프로그램", checks, body, "내일 다시 확인", null, null);
    }

    private LabRecordLogDto.Input briefing(LocalDate date, List<String> checks) {
        return new LabRecordLogDto.Input("SAFETY_BRIEFING", date, kim.getId(), "박간호", checks, "낙상 예방 중심으로 설명",
                null, null, null);
    }

    private LabRecordLogDto.Input receipt(Long elderlyId, Long amount, List<String> checks, String number) {
        return new LabRecordLogDto.Input("CASH_RECEIPT", DAY, elderlyId, "정경리", checks, null, null, number, amount);
    }

    @Test
    void dutyLogNeedsBodyAndAllowedChecksOnly() {
        LabRecordLogDto.View saved = service.create(duty("PROGRAM_MANAGER_LOG", DAY,
                List.of("RECORD_REVIEW", "COGNITIVE_CHECK", "RECORD_REVIEW"), "인지활동형 프로그램 제공 확인"));
        // 허용 목록 순서로 정리하고 중복은 합친다
        assertThat(saved.checks()).containsExactly("COGNITIVE_CHECK", "RECORD_REVIEW");
        assertThat(saved.elderlyId()).isNull();

        assertRejected(() -> service.create(duty("PROGRAM_MANAGER_LOG", DAY, List.of(), "  ")),
                HttpStatus.BAD_REQUEST, "내용");
        // 다른 종류의 항목은 허용되지 않는다
        assertRejected(() -> service.create(duty("PROGRAM_MANAGER_LOG", DAY, List.of("FIELD_GUIDE"), "내용")),
                HttpStatus.BAD_REQUEST, "확인 항목");
        assertRejected(() -> service.create(duty("NOPE", DAY, List.of(), "내용")),
                HttpStatus.BAD_REQUEST, "종류");
    }

    @Test
    void dateNotInFutureAndAuthorRequired() {
        assertRejected(() -> service.create(duty("FACILITY_DIARY", LabValidation.seoulToday().plusDays(1), List.of(),
                "내용")), HttpStatus.BAD_REQUEST, "오늘 이후");
        assertRejected(() -> service.create(duty("FACILITY_DIARY", null, List.of(), "내용")),
                HttpStatus.BAD_REQUEST, "기록일");
        LabRecordLogDto.Input noAuthor = new LabRecordLogDto.Input("FACILITY_DIARY", DAY, null, " ", List.of(), "내용",
                null, null, null);
        assertRejected(() -> service.create(noAuthor), HttpStatus.BAD_REQUEST, "작성자");
    }

    @Test
    void dutyKindsRejectElderAmountAndExtra() {
        LabRecordLogDto.Input withElder = new LabRecordLogDto.Input("FACILITY_DIARY", DAY, kim.getId(), "이운영",
                List.of(), "내용", null, null, null);
        assertRejected(() -> service.create(withElder), HttpStatus.BAD_REQUEST, "어르신을 지정하지");
        LabRecordLogDto.Input withAmount = new LabRecordLogDto.Input("FACILITY_DIARY", DAY, null, "이운영",
                List.of(), "내용", null, null, 1000L);
        assertRejected(() -> service.create(withAmount), HttpStatus.BAD_REQUEST, "금액 칸이 없습니다");
    }

    @Test
    void safetyBriefingNeedsElderTopicAndAudience() {
        LabRecordLogDto.View ok = service.create(briefing(DAY, List.of("TO_GUARDIAN", "FALL", "EMERGENCY")));
        assertThat(ok.checks()).containsExactly("FALL", "EMERGENCY", "TO_GUARDIAN");
        assertThat(ok.elderlyName()).isEqualTo("김어르신");

        assertRejected(() -> service.create(briefing(DAY, List.of("FALL"))), HttpStatus.BAD_REQUEST, "대상");
        assertRejected(() -> service.create(briefing(DAY, List.of("TO_ELDER"))), HttpStatus.BAD_REQUEST, "안전 항목");
        LabRecordLogDto.Input noElder = new LabRecordLogDto.Input("SAFETY_BRIEFING", DAY, null, "박간호",
                List.of("FALL", "TO_ELDER"), null, null, null, null);
        assertRejected(() -> service.create(noElder), HttpStatus.BAD_REQUEST, "어르신");
    }

    @Test
    void elderMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertRejected(() -> service.create(receipt(stranger.getId(), 10000L, List.of("INCOME_DEDUCTION"), null)),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.list("CASH_RECEIPT", null, null, stranger.getId()), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void cashReceiptNeedsAmountAndOneKind() {
        LabRecordLogDto.View saved = service.create(receipt(kim.getId(), 85000L, List.of("EXPENSE_PROOF"), "G1234567"));
        assertThat(saved.amount()).isEqualTo(85000L);
        assertThat(saved.extra()).isEqualTo("G1234567");

        assertRejected(() -> service.create(receipt(kim.getId(), null, List.of("INCOME_DEDUCTION"), null)),
                HttpStatus.BAD_REQUEST, "금액");
        assertRejected(() -> service.create(receipt(kim.getId(), 0L, List.of("INCOME_DEDUCTION"), null)),
                HttpStatus.BAD_REQUEST, "금액");
        assertRejected(() -> service.create(receipt(kim.getId(), -5L, List.of("INCOME_DEDUCTION"), null)),
                HttpStatus.BAD_REQUEST, "금액");
        assertRejected(() -> service.create(receipt(kim.getId(), 2_000_000_000L, List.of("INCOME_DEDUCTION"), null)),
                HttpStatus.BAD_REQUEST, "금액");
        assertRejected(() -> service.create(receipt(kim.getId(), 1000L, List.of(), null)), HttpStatus.BAD_REQUEST, "구분");
        assertRejected(() -> service.create(receipt(kim.getId(), 1000L, List.of("INCOME_DEDUCTION", "EXPENSE_PROOF"),
                null)), HttpStatus.BAD_REQUEST, "구분");
    }

    @Test
    void textEncryptedAtRest() {
        LabRecordLogDto.Input in = new LabRecordLogDto.Input("CASH_RECEIPT", DAY, kim.getId(), "정경리",
                List.of("INCOME_DEDUCTION"), "비고 메모입니다", "조치 메모입니다", "승인-98765", 120000L);
        service.create(in);
        service.create(duty("FACILITY_DIARY", DAY, List.of("OPENING"), "시설 점검 특이사항 기록"));
        em.flush();
        em.clear();

        assertEncryptedAtRest(jdbc, "lab_record_logs", "body", "비고 메모입니다");
        assertEncryptedAtRest(jdbc, "lab_record_logs", "action", "조치 메모입니다");
        assertEncryptedAtRest(jdbc, "lab_record_logs", "extra", "승인-98765");
        LabRecordLogDto.View read = service.list("CASH_RECEIPT", "2026-03", null, null).get(0);
        assertThat(read.body()).isEqualTo("비고 메모입니다");
        assertThat(read.extra()).isEqualTo("승인-98765");
        assertThat(read.amount()).isEqualTo(120000L);
    }

    @Test
    void listFiltersByKindMonthYearAndElder() {
        service.create(duty("FACILITY_DIARY", LocalDate.of(2026, 3, 10), List.of(), "3월 일지"));
        service.create(duty("FACILITY_DIARY", LocalDate.of(2026, 4, 2), List.of(), "4월 일지"));
        service.create(duty("PROGRAM_MANAGER_LOG", LocalDate.of(2026, 3, 11), List.of(), "다른 종류"));
        service.create(briefing(LocalDate.of(2025, 11, 5), List.of("FALL", "TO_ELDER")));

        assertThat(service.list("FACILITY_DIARY", "2026-03", null, null)).extracting(LabRecordLogDto.View::body)
                .containsExactly("3월 일지");
        assertThat(service.list("FACILITY_DIARY", null, 2026, null)).extracting(LabRecordLogDto.View::body)
                .containsExactly("4월 일지", "3월 일지"); // 최신순
        assertThat(service.list("SAFETY_BRIEFING", null, 2025, null)).hasSize(1);
        assertThat(service.list("SAFETY_BRIEFING", null, 2026, null)).isEmpty();
        assertThat(service.list("SAFETY_BRIEFING", null, null, kim.getId())).hasSize(1);

        assertRejected(() -> service.list("FACILITY_DIARY", null, null, null), HttpStatus.BAD_REQUEST, "월(month)");
        assertRejected(() -> service.list("NOPE", "2026-03", null, null), HttpStatus.BAD_REQUEST, "종류");
        assertRejected(() -> service.list("FACILITY_DIARY", null, 1999, null), HttpStatus.BAD_REQUEST, "연도");
    }

    @Test
    void updateKeepsKindAndOtherCompanyCannotTouch() {
        LabRecordLogDto.View saved = service.create(duty("FACILITY_DIARY", DAY, List.of("MEAL"), "처음 내용"));
        LabRecordLogDto.View updated = service.update(saved.id(),
                duty("FACILITY_DIARY", DAY, List.of("MEAL", "VISITOR"), "고친 내용"));
        assertThat(updated.body()).isEqualTo("고친 내용");
        assertThat(updated.checks()).containsExactly("MEAL", "VISITOR");
        assertRejected(() -> service.update(saved.id(), duty("PROGRAM_MANAGER_LOG", DAY, List.of(), "x")),
                HttpStatus.BAD_REQUEST, "종류는 바꿀 수 없습니다");

        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertRejected(() -> service.update(saved.id(), duty("FACILITY_DIARY", DAY, List.of(), "침범")),
                HttpStatus.NOT_FOUND, "기록을 찾을 수 없습니다");
        assertRejected(() -> service.delete(saved.id()), HttpStatus.NOT_FOUND, null);
        assertThat(logs.count()).isEqualTo(1);

        access.companyId = forest.getId();
        service.delete(saved.id());
        assertThat(logs.count()).isZero();
    }
}
