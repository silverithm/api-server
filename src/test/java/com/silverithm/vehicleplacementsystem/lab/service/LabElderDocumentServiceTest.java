package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabElderDocumentDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabElderDocumentRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabElderDocumentServiceTest {

    @Autowired private LabElderDocumentService service;
    @Autowired private LabElderDocumentRepository documents;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly park;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김어르신");
        park = LabTestFixtures.elder(em, forest, "박어르신");
        access.companyId = forest.getId();
    }

    private LabElderDocumentDto.Input input(Long elderlyId, String type, LocalDate issued, LocalDate expires) {
        return new LabElderDocumentDto.Input(elderlyId, type, "급여계약서", issued, expires, "사무실 2번 서랍", null,
                "원본 보관");
    }

    // 어르신은 같은 기관 소속이어야 한다 — 다른 기관 어르신은 존재 여부도 알려 주지 않고 404
    @Test
    void elderMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "남어르신");

        assertRejected(() -> service.create(input(stranger.getId(), "CONTRACT", null, null)),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.create(input(999_999L, "CONTRACT", null, null)), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.create(input(null, "CONTRACT", null, null)), HttpStatus.BAD_REQUEST,
                "어르신을 선택");
        assertRejected(() -> service.list(stranger.getId()), HttpStatus.NOT_FOUND, null);
        assertThat(documents.count()).isZero();

        LabElderDocumentDto.View ok = service.create(input(kim.getId(), "CONTRACT", LocalDate.of(2026, 1, 5), null));
        assertThat(ok.elderlyName()).isEqualTo("김어르신");
        assertThat(ok.location()).isEqualTo("사무실 2번 서랍");
        assertRejected(() -> service.update(ok.id(), input(stranger.getId(), "CONTRACT", null, null)),
                HttpStatus.NOT_FOUND, null);

        // 다른 기관 사용자는 이 서류를 고치거나 지울 수 없다
        access.companyId = other.getId();
        assertRejected(() -> service.update(ok.id(), input(stranger.getId(), "CONTRACT", null, null)),
                HttpStatus.NOT_FOUND, "서류를 찾을 수 없습니다");
        assertRejected(() -> service.delete(ok.id()), HttpStatus.NOT_FOUND, "서류를 찾을 수 없습니다");
        assertThat(service.list(null)).isEmpty();
        access.companyId = forest.getId();
        service.delete(ok.id());
        assertThat(documents.count()).isZero();
    }

    // 만료일은 발급일보다 빠를 수 없다(같은 날은 가능). 종류·제목·글자 수도 지킨다
    @Test
    void expiresBeforeIssuedRejected() {
        assertRejected(() -> service.create(input(kim.getId(), "CONTRACT", LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 9))), HttpStatus.BAD_REQUEST, "만료일은 발급일보다 빠를 수 없습니다");
        assertThat(documents.count()).isZero();
        assertThat(service.create(input(kim.getId(), "CONTRACT", LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10))).expiresOn()).isEqualTo(LocalDate.of(2026, 3, 10));
        // 한쪽만 있으면 비교하지 않는다
        service.create(input(kim.getId(), "RECOGNITION", null, LocalDate.of(2020, 1, 1)));
        service.create(input(kim.getId(), "USE_PLAN", LocalDate.of(2026, 1, 1), null));

        LabElderDocumentDto.View saved = service.create(input(kim.getId(), "OTHER", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1)));
        assertRejected(() -> service.update(saved.id(), input(kim.getId(), "OTHER", LocalDate.of(2026, 1, 1),
                LocalDate.of(2025, 12, 31))), HttpStatus.BAD_REQUEST, "만료일은 발급일보다");
        assertThat(service.list(kim.getId())).extracting(LabElderDocumentDto.View::expiresOn)
                .contains(LocalDate.of(2027, 1, 1));

        assertRejected(() -> service.create(input(kim.getId(), "PASSPORT", null, null)), HttpStatus.BAD_REQUEST,
                "서류 종류");
        assertRejected(() -> service.create(new LabElderDocumentDto.Input(kim.getId(), "OTHER", " ", null, null,
                null, null, null)), HttpStatus.BAD_REQUEST, "서류 제목");
        assertRejected(() -> service.create(new LabElderDocumentDto.Input(kim.getId(), "OTHER", "가".repeat(101),
                null, null, null, null, null)), HttpStatus.BAD_REQUEST, "100자");
        assertRejected(() -> service.create(new LabElderDocumentDto.Input(kim.getId(), "OTHER", "제목", null, null,
                "가".repeat(101), null, null)), HttpStatus.BAD_REQUEST, "100자");
        assertRejected(() -> service.create(new LabElderDocumentDto.Input(kim.getId(), "OTHER", "제목", null, null,
                null, null, "가".repeat(501))), HttpStatus.BAD_REQUEST, "500자");
        assertRejected(() -> service.create(new LabElderDocumentDto.Input(kim.getId(), "OTHER", "제목",
                LocalDate.of(1900, 1, 1), null, null, null, null)), HttpStatus.BAD_REQUEST, "2000~2100");
    }

    // 어르신 필터 — 고르면 그 어르신 것만, 안 고르면 기관 전체(어르신 순)
    @Test
    void elderFilter() {
        service.create(input(park.getId(), "CARE_PLAN", null, null));
        service.create(input(kim.getId(), "RISK_ASSESSMENT", null, null));
        service.create(input(kim.getId(), "CONTRACT", null, null));

        assertThat(service.list(kim.getId())).extracting(LabElderDocumentDto.View::docType)
                .containsExactly("CONTRACT", "RISK_ASSESSMENT");
        assertThat(service.list(kim.getId())).allMatch(d -> d.elderlyName().equals("김어르신"));
        assertThat(service.list(park.getId())).extracting(LabElderDocumentDto.View::docType)
                .containsExactly("CARE_PLAN");
        assertThat(service.list(null)).hasSize(3);

        // 어르신을 지우면(FK CASCADE는 MySQL) 서비스는 다른 어르신의 서류를 건드리지 않는다
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "남어르신");
        access.companyId = other.getId();
        service.create(input(stranger.getId(), "CONTRACT", null, null));
        assertThat(service.list(null)).hasSize(1);
        access.companyId = forest.getId();
        assertThat(service.list(null)).hasSize(3);
    }
}
