package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabVaccinationDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabVaccinationRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabVaccinationServiceTest {

    @Autowired private LabVaccinationService service;
    @Autowired private LabVaccinationRepository vaccinations;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly lee;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김어르신");
        lee = LabTestFixtures.elder(em, forest, "이어르신");
        access.companyId = forest.getId();
    }

    private static LabVaccinationDto.Input done(Long elderlyId, int year, String vaccine, LocalDate on) {
        return new LabVaccinationDto.Input(elderlyId, year, vaccine, "DONE", on, "행복의원", "CERT", null, null);
    }

    private static LabVaccinationDto.Input other(Long elderlyId, int year, String status, String reason) {
        return new LabVaccinationDto.Input(elderlyId, year, "INFLUENZA", status, null, null, null, reason, null);
    }

    // 어르신 x 연도 x 백신마다 한 건 — 다시 저장하면 덮어쓰고, 이름·작성자가 채워지며 다른 조합은 따로 남는다
    @Test
    void upsertByElderYearVaccine() {
        LabVaccinationDto.View first = service.upsert(done(kim.getId(), 2025, "INFLUENZA", LocalDate.of(2025, 10, 20)));
        assertThat(first.elderlyName()).isEqualTo("김어르신");
        assertThat(first.writerName()).isEqualTo("admin@forest.kr");
        assertThat(first.status()).isEqualTo("DONE");
        assertThat(first.verifiedBy()).isEqualTo("CERT");

        // 같은 키로 거부로 바꾸면 같은 행이고 접종일·확인 방법·기관은 비워진다
        LabVaccinationDto.View second = service.upsert(other(kim.getId(), 2025, "REFUSED", "보호자 거부"));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(vaccinations.count()).isEqualTo(1);
        assertThat(second.status()).isEqualTo("REFUSED");
        assertThat(second.givenOn()).isNull();
        assertThat(second.verifiedBy()).isNull();
        assertThat(second.institution()).isNull();
        assertThat(second.reason()).isEqualTo("보호자 거부");

        // 연도·백신·어르신이 다르면 다른 행
        service.upsert(done(kim.getId(), 2025, "PNEUMOCOCCAL", LocalDate.of(2025, 3, 2)));
        service.upsert(done(kim.getId(), 2024, "INFLUENZA", LocalDate.of(2024, 11, 1)));
        service.upsert(done(lee.getId(), 2025, "INFLUENZA", LocalDate.of(2025, 10, 21)));
        assertThat(vaccinations.count()).isEqualTo(4);

        assertThat(service.list("2025", null)).hasSize(3);
        assertThat(service.list(null, kim.getId())).extracting(LabVaccinationDto.View::seasonYear)
                .containsExactly(2025, 2025, 2024);
        assertThat(service.list("2025", kim.getId())).hasSize(2);
        assertRejected(() -> service.list(null, null), HttpStatus.BAD_REQUEST, "year 또는 elderlyId");
        assertRejected(() -> service.list("25", null), HttpStatus.BAD_REQUEST, "YYYY");

        // 지우기 — 다른 기관은 못 지운다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertRejected(() -> service.delete(first.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        assertThat(service.list("2025", null)).isEmpty();
        access.companyId = forest.getId();
        service.delete(first.id());
        assertThat(vaccinations.count()).isEqualTo(3);
    }

    // 접종했으면 접종일(접종 연도 안)과 확인 방법이 필수 — 하나라도 없거나 틀리면 400이고 저장되지 않는다
    @Test
    void doneRequiresDateAndVerification() {
        assertRejected(() -> service.upsert(done(kim.getId(), 2025, "INFLUENZA", null)), HttpStatus.BAD_REQUEST,
                "접종일");
        assertRejected(() -> service.upsert(new LabVaccinationDto.Input(kim.getId(), 2025, "INFLUENZA", "DONE",
                LocalDate.of(2025, 10, 20), null, null, null, null)), HttpStatus.BAD_REQUEST, "확인 방법");
        assertRejected(() -> service.upsert(new LabVaccinationDto.Input(kim.getId(), 2025, "INFLUENZA", "DONE",
                LocalDate.of(2025, 10, 20), null, "FAX", null, null)), HttpStatus.BAD_REQUEST, "확인 방법");
        // 접종일의 해가 접종 연도와 다르면 거절
        assertRejected(() -> service.upsert(done(kim.getId(), 2025, "INFLUENZA", LocalDate.of(2024, 12, 31))),
                HttpStatus.BAD_REQUEST, "접종 연도");
        assertRejected(() -> service.upsert(done(kim.getId(), 2025, "INFLUENZA", LocalDate.of(2026, 1, 1))),
                HttpStatus.BAD_REQUEST, "접종 연도");
        assertThat(vaccinations.count()).isZero();

        // 연도 경계와 입력 값 범위
        assertThat(service.upsert(done(kim.getId(), 2025, "INFLUENZA", LocalDate.of(2025, 1, 1))).givenOn())
                .isEqualTo(LocalDate.of(2025, 1, 1));
        assertRejected(() -> service.upsert(done(kim.getId(), 2019, "INFLUENZA", LocalDate.of(2019, 5, 1))),
                HttpStatus.BAD_REQUEST, "접종 연도");
        assertRejected(() -> service.upsert(done(kim.getId(), 2101, "INFLUENZA", LocalDate.of(2025, 5, 1))),
                HttpStatus.BAD_REQUEST, "접종 연도");
        assertRejected(() -> service.upsert(done(kim.getId(), 2025, "MEASLES", LocalDate.of(2025, 5, 1))),
                HttpStatus.BAD_REQUEST, "백신");
        assertRejected(() -> service.upsert(new LabVaccinationDto.Input(kim.getId(), 2025, "INFLUENZA", "MAYBE",
                null, null, null, null, null)), HttpStatus.BAD_REQUEST, "접종 상태");
        assertRejected(() -> service.upsert(new LabVaccinationDto.Input(kim.getId(), 2025, "INFLUENZA", "DONE",
                LocalDate.of(2025, 5, 1), "가".repeat(101), "CERT", null, null)), HttpStatus.BAD_REQUEST, "접종 기관");
        // 거절된 저장은 기존 값을 바꾸지 않는다
        assertThat(vaccinations.findAll()).singleElement().satisfies(v -> {
            assertThat(v.getStatus()).isEqualTo("DONE");
            assertThat(v.getGivenOn()).isEqualTo(LocalDate.of(2025, 1, 1));
        });
    }

    // 거부·제외는 사유가 필수, 미확인(UNKNOWN)은 사유 없이도 저장된다
    @Test
    void refusedRequiresReason() {
        assertRejected(() -> service.upsert(other(kim.getId(), 2025, "REFUSED", null)), HttpStatus.BAD_REQUEST, "사유");
        assertRejected(() -> service.upsert(other(kim.getId(), 2025, "REFUSED", "   ")), HttpStatus.BAD_REQUEST, "사유");
        assertRejected(() -> service.upsert(other(kim.getId(), 2025, "EXCLUDED", null)), HttpStatus.BAD_REQUEST, "사유");
        assertRejected(() -> service.upsert(other(kim.getId(), 2025, "EXCLUDED", "가".repeat(201))),
                HttpStatus.BAD_REQUEST, "사유");
        assertThat(vaccinations.count()).isZero();

        assertThat(service.upsert(other(kim.getId(), 2025, "REFUSED", "접종 후 이상반응 우려")).reason())
                .isEqualTo("접종 후 이상반응 우려");
        assertThat(service.upsert(other(lee.getId(), 2025, "EXCLUDED", "계란 알레르기")).status()).isEqualTo("EXCLUDED");
        LabVaccinationDto.View unknown = service.upsert(other(lee.getId(), 2024, "UNKNOWN", "남아 있던 사유"));
        assertThat(unknown.status()).isEqualTo("UNKNOWN");
        assertThat(unknown.reason()).isNull();
        // 접종으로 바꾸면 거부 사유는 지워진다
        LabVaccinationDto.View redone = service.upsert(done(kim.getId(), 2025, "INFLUENZA", LocalDate.of(2025, 11, 3)));
        assertThat(redone.reason()).isNull();
        assertThat(redone.status()).isEqualTo("DONE");
    }

    // 접종일이 오늘(서울)보다 미래면 400, 오늘은 통과
    @Test
    void futureDateRejected() {
        LocalDate today = LabValidation.seoulToday();
        assertRejected(() -> service.upsert(done(kim.getId(), today.getYear(), "INFLUENZA", today.plusDays(1))),
                HttpStatus.BAD_REQUEST, "오늘 이후");
        assertRejected(() -> service.upsert(done(kim.getId(), today.getYear() + 1, "INFLUENZA",
                LocalDate.of(today.getYear() + 1, 1, 2))), HttpStatus.BAD_REQUEST, "오늘 이후");
        // 거부로 보내도 미래 접종일은 말이 안 되므로 거절한다
        assertRejected(() -> service.upsert(new LabVaccinationDto.Input(kim.getId(), today.getYear(), "INFLUENZA",
                "REFUSED", today.plusDays(1), null, null, "거부", null)), HttpStatus.BAD_REQUEST, "오늘 이후");
        assertThat(vaccinations.count()).isZero();
        assertThat(service.upsert(done(kim.getId(), today.getYear(), "INFLUENZA", today)).givenOn()).isEqualTo(today);
    }

    // 다른 기관·없는 어르신은 404(존재를 알려 주지 않는다)이고 아무것도 저장되지 않는다
    @Test
    void elderMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly outsider = LabTestFixtures.elder(em, other, "외부어르신");
        assertRejected(() -> service.upsert(done(outsider.getId(), 2025, "INFLUENZA", LocalDate.of(2025, 10, 20))),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.upsert(done(999_999L, 2025, "INFLUENZA", LocalDate.of(2025, 10, 20))),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.upsert(done(null, 2025, "INFLUENZA", LocalDate.of(2025, 10, 20))),
                HttpStatus.BAD_REQUEST, "어르신");
        assertRejected(() -> service.list(null, outsider.getId()), HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertThat(vaccinations.count()).isZero();

        // 다른 기관이 같은 어르신 id로 만든 기록이 내 기관 목록에 섞이지 않는다
        access.companyId = other.getId();
        service.upsert(done(outsider.getId(), 2025, "INFLUENZA", LocalDate.of(2025, 10, 20)));
        access.companyId = forest.getId();
        assertThat(service.list("2025", null)).isEmpty();
    }
}
