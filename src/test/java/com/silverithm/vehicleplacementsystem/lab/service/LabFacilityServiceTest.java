package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.lab.dto.LabFacilityDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabFacilityLogRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabFacilityProfileRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabFacilityServiceTest {

    @Autowired private LabFacilityService service;
    @Autowired private LabFacilityProfileRepository profiles;
    @Autowired private LabFacilityLogRepository logs;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private static final LocalDate TODAY = LabValidation.seoulToday();

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private static void assertBad(Runnable call, String message) {
        LabTestAsserts.assertRejected(call, HttpStatus.BAD_REQUEST, message);
    }

    private static LabFacilityDto.ProfileInput profile(String gasKind, Integer lpgKg, Map<String, String> overrides) {
        return new LabFacilityDto.ProfileInput(LocalDate.of(2012, 5, 20), 850, "FULL", "GENERAL", gasKind, lpgKg,
                "CENTER", "SMALL", "NONE", overrides, "김안전", "1층 사무실에 보관");
    }

    private static LabFacilityDto.LogInput log(String code, LocalDate doneOn, LocalDate reportedOn,
                                               LocalDate validUntil) {
        return new LabFacilityDto.LogInput(code, doneOn, "VENDOR", "대한소방", "PASS", null, reportedOn, validUntil,
                "사무실 서류함");
    }

    // 저장한 적이 없으면 기본값(id null)을 주고, 몇 번을 저장해도 기관당 한 줄이다
    @Test
    void profileUpsertOnePerCompany() {
        LabFacilityDto.ProfileView empty = service.getProfile();
        assertThat(empty.id()).isNull();
        assertThat(empty.sprinkler()).isEqualTo("UNKNOWN");
        assertThat(empty.powerKind()).isEqualTo("UNKNOWN");
        assertThat(empty.gasKind()).isEqualTo("UNKNOWN");
        assertThat(empty.elevator()).isEqualTo("UNKNOWN");
        assertThat(empty.waterTank()).isEqualTo("UNKNOWN");
        assertThat(empty.septic()).isEqualTo("UNKNOWN");
        assertThat(empty.appliesOverride()).isEmpty();
        assertThat(empty.approvedOn()).isNull();

        LabFacilityDto.ProfileView first = service.saveProfile(profile("LPG", 300, Map.of("ELEV_ANNUAL", "NO")));
        assertThat(first.id()).isNotNull();
        assertThat(first.gasKind()).isEqualTo("LPG");
        assertThat(first.lpgCapacityKg()).isEqualTo(300);
        assertThat(first.appliesOverride()).containsEntry("ELEV_ANNUAL", "NO");
        assertThat(first.writerName()).isEqualTo("숲속 관리자");

        // LPG가 아니면 용량을 비운다. 칸을 비우면 UNKNOWN으로 돌아간다
        LabFacilityDto.ProfileView second = service.saveProfile(new LabFacilityDto.ProfileInput(null, null, null, null,
                "CITY", 300, null, null, null, null, null, null));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.lpgCapacityKg()).isNull();
        assertThat(second.sprinkler()).isEqualTo("UNKNOWN");
        assertThat(second.appliesOverride()).isEmpty();
        assertThat(second.approvedOn()).isNull();
        assertThat(profiles.count()).isEqualTo(1);
        assertThat(service.getProfile().gasKind()).isEqualTo("CITY");

        // 다른 기관은 자기 줄을 따로 갖고, 서로 보이지 않는다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.getProfile().id()).isNull();
        service.saveProfile(profile("NONE", null, null));
        assertThat(profiles.count()).isEqualTo(2);
        access.companyId = forest.getId();
        assertThat(service.getProfile().gasKind()).isEqualTo("CITY");

        // 값 범위
        assertBad(() -> service.saveProfile(profile("BIOGAS", null, null)), "가스");
        assertBad(() -> service.saveProfile(profile("LPG", 100_001, null)), "LPG");
        assertBad(() -> service.saveProfile(profile("LPG", -1, null)), "LPG");
        assertBad(() -> service.saveProfile(new LabFacilityDto.ProfileInput(null, 0, null, null, null, null, null,
                null, null, null, null, null)), "연면적");
        assertBad(() -> service.saveProfile(new LabFacilityDto.ProfileInput(TODAY.plusDays(1), null, null, null, null,
                null, null, null, null, null, null, null)), "사용승인일");
        assertBad(() -> service.saveProfile(new LabFacilityDto.ProfileInput(null, null, null, null, null, null, null,
                null, null, null, "가".repeat(51), null)), "50자");
        assertThat(profiles.count()).isEqualTo(2);
    }

    // 모르는 항목 코드나 YES/NO 아닌 값으로 직접 지정하면 400
    @Test
    void unknownOverrideCodeRejected() {
        assertBad(() -> service.saveProfile(profile("NONE", null, Map.of("FIRE_NEW", "YES"))), "항목 코드");
        assertBad(() -> service.saveProfile(profile("NONE", null, Map.of("ELEV_ANNUAL", "MAYBE"))), "YES 또는 NO");
        Map<String, String> nullValue = new LinkedHashMap<>();
        nullValue.put("ELEV_ANNUAL", null);
        assertBad(() -> service.saveProfile(profile("NONE", null, nullValue)), "YES 또는 NO");
        assertThat(profiles.count()).isZero();

        Map<String, String> ok = new LinkedHashMap<>();
        for (String code : LabFacilityService.FACILITY_ITEM_CODES) {
            ok.put(code, "YES");
        }
        assertThat(service.saveProfile(profile("NONE", null, ok)).appliesOverride()).hasSize(19);
        assertThat(LabFacilityService.FACILITY_ITEM_CODES).hasSize(19).doesNotHaveDuplicates();
    }

    // 목록 허용 밖 항목 코드는 저장도 조회도 400
    @Test
    void logUnknownItemRejected() {
        assertBad(() -> service.createLog(log("FIRE_UNKNOWN", TODAY, null, null)), "항목 코드");
        assertBad(() -> service.createLog(log(null, TODAY, null, null)), "항목 코드");
        assertBad(() -> service.listLogs(null, "fire_operation"), "항목 코드");
        assertBad(() -> service.listLogs(null, null), "year 또는 itemCode");
        assertBad(() -> service.listLogs("20x6", null), "YYYY");
        assertThat(logs.count()).isZero();

        // 수정으로 모르는 항목으로 바꾸는 것도 막힌다
        Long id = service.createLog(log("ELEV_ANNUAL", TODAY, null, null)).id();
        assertBad(() -> service.updateLog(id, log("NOPE", TODAY, null, null)), "항목 코드");
        for (String code : LabFacilityService.FACILITY_ITEM_CODES) {
            LocalDate until = code.startsWith("INSURANCE") ? TODAY.plusYears(1) : null;
            assertThat(service.createLog(log(code, TODAY, null, until)).itemCode()).isEqualTo(code);
        }
    }

    // 보험 두 항목은 만기가 필수이고 실시일보다 뒤여야 한다. 다른 항목의 만기는 조용히 비운다
    @Test
    void insuranceRequiresValidUntil() {
        for (String code : List.of("INSURANCE_FACILITY", "INSURANCE_PROFESSIONAL")) {
            assertBad(() -> service.createLog(log(code, TODAY, null, null)), "보험 만기일");
            assertBad(() -> service.createLog(log(code, TODAY, null, TODAY)), "뒤여야");
            assertBad(() -> service.createLog(log(code, TODAY, null, TODAY.minusDays(1))), "뒤여야");
            LabFacilityDto.LogView ok = service.createLog(log(code, TODAY, null, TODAY.plusYears(1)));
            assertThat(ok.validUntil()).isEqualTo(TODAY.plusYears(1));
        }
        assertThat(logs.count()).isEqualTo(2);

        LabFacilityDto.LogView other = service.createLog(log("ELEV_ANNUAL", TODAY, null, TODAY.plusYears(1)));
        assertThat(other.validUntil()).isNull();
        // 수정으로 보험 항목이 만기 없이 저장되는 일도 없다
        assertBad(() -> service.updateLog(other.id(), log("INSURANCE_FACILITY", TODAY, null, null)), "보험 만기일");
    }

    // 소방 보고일은 소방 자체점검 두 항목만 저장하고 done_on 이상이어야 한다. 다른 항목은 조용히 비운다
    @Test
    void reportedOnOnlyForFireItems() {
        LocalDate done = TODAY.minusDays(10);
        for (String code : List.of("FIRE_OPERATION", "FIRE_COMPREHENSIVE")) {
            assertThat(service.createLog(log(code, done, done.plusDays(7), null)).reportedOn()).isEqualTo(done.plusDays(7));
            assertThat(service.createLog(log(code, done, done, null)).reportedOn()).isEqualTo(done);
            assertThat(service.createLog(log(code, done, null, null)).reportedOn()).isNull();
            assertBad(() -> service.createLog(log(code, done, done.minusDays(1), null)), "보고일");
            assertBad(() -> service.createLog(log(code, done, TODAY.plusDays(1), null)), "소방 결과 보고일");
        }
        for (String code : List.of("FIRE_DRILL_LEGAL", "ELEC_GENERAL", "INSURANCE_FACILITY")) {
            LocalDate until = code.startsWith("INSURANCE") ? TODAY.plusYears(1) : null;
            // 다른 항목에서는 보고일이 앞서 있어도 거절하지 않고 비운다
            assertThat(service.createLog(log(code, done, done.minusDays(3), until)).reportedOn()).isNull();
        }
    }

    // 실시일이 미래이면 400, 오늘은 된다. 범위 밖 먼 날짜도 400
    @Test
    void futureDoneOnRejected() {
        assertBad(() -> service.createLog(log("TANK_CLEAN", TODAY.plusDays(1), null, null)), "실시일");
        assertBad(() -> service.createLog(log("TANK_CLEAN", null, null, null)), "실시일");
        assertBad(() -> service.createLog(log("TANK_CLEAN", LocalDate.of(1999, 1, 1), null, null)), "실시일");
        assertThat(logs.count()).isZero();
        assertThat(service.createLog(log("TANK_CLEAN", TODAY, null, null)).doneOn()).isEqualTo(TODAY);

        Long id = service.listLogs(String.valueOf(TODAY.getYear()), null).get(0).id();
        assertBad(() -> service.updateLog(id, log("TANK_CLEAN", TODAY.plusDays(1), null, null)), "실시일");

        // 나머지 칸 검증
        assertBad(() -> service.createLog(new LabFacilityDto.LogInput("TANK_CLEAN", TODAY, "ROBOT", null, "PASS", null,
                null, null, null)), "실시자");
        assertBad(() -> service.createLog(new LabFacilityDto.LogInput("TANK_CLEAN", TODAY, "SELF", null, "OK", null,
                null, null, null)), "결과");
        assertBad(() -> service.createLog(new LabFacilityDto.LogInput("TANK_CLEAN", TODAY, "SELF", null, "PASS",
                "가".repeat(501), null, null, null)), "500자");
    }

    // 기록은 기관 범위 안에서만 보이고 고쳐지고 지워진다. 목록은 실시일 최신순
    @Test
    void logsScopedToCompany() {
        // 날짜는 고정해 해 경계에 따라 결과가 흔들리지 않게 한다
        LabFacilityDto.LogView older = service.createLog(log("ELEV_ANNUAL", LocalDate.of(2025, 3, 1), null, null));
        LabFacilityDto.LogView newer = service.createLog(log("ELEV_ANNUAL", LocalDate.of(2025, 6, 1), null, null));
        service.createLog(log("TANK_CLEAN", LocalDate.of(2025, 1, 2), null, null));
        service.createLog(log("TANK_CLEAN", LocalDate.of(2024, 12, 31), null, null));

        assertThat(service.listLogs(null, "ELEV_ANNUAL")).extracting(LabFacilityDto.LogView::id)
                .containsExactly(newer.id(), older.id());
        assertThat(service.listLogs(null, "TANK_CLEAN")).extracting(LabFacilityDto.LogView::doneOn)
                .containsExactly(LocalDate.of(2025, 1, 2), LocalDate.of(2024, 12, 31));
        assertThat(service.listLogs("2025", null)).hasSize(3);
        assertThat(service.listLogs("2024", "TANK_CLEAN")).hasSize(1);
        assertThat(service.listLogs("2024", "ELEV_ANNUAL")).isEmpty();
        assertThat(service.listLogs("2025", "ELEV_ANNUAL")).hasSize(2);
        assertThat(newer.writerName()).isEqualTo("숲속 관리자");

        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.listLogs(null, "ELEV_ANNUAL")).isEmpty();
        assertThat(service.listLogs("2025", null)).isEmpty();
        LabTestAsserts.assertRejected(() -> service.updateLog(newer.id(), log("ELEV_ANNUAL", TODAY, null, null)),
                HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        LabTestAsserts.assertRejected(() -> service.deleteLog(newer.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        assertThat(logs.count()).isEqualTo(4);

        access.companyId = forest.getId();
        assertThat(service.updateLog(newer.id(), log("ELEV_ANNUAL", TODAY, null, null)).doneOn()).isEqualTo(TODAY);
        service.deleteLog(newer.id());
        assertThat(logs.count()).isEqualTo(3);
        LabTestAsserts.assertRejected(() -> service.deleteLog(newer.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
    }
}
