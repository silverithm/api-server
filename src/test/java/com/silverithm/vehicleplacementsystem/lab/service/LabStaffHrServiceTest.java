package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffHrDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffCertRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffProfileRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabStaffHrServiceTest {

    @Autowired private LabStaffHrService service;
    @Autowired private LabStaffProfileRepository profiles;
    @Autowired private LabStaffCertRepository certs;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Member lee;
    private Member park;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        lee = LabTestFixtures.member(em, forest, "이요양", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        park = LabTestFixtures.member(em, forest, "박간호", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        access.companyId = forest.getId();
    }

    private LabStaffHrDto.ProfileInput profile(Long memberId, String job, Integer birthYear) {
        return new LabStaffHrDto.ProfileInput(memberId, LocalDate.of(2024, 3, 1), job, birthYear,
                LocalDate.of(2023, 11, 20), false, "메모");
    }

    private LabStaffHrDto.CertInput cert(Long memberId, String kind, LocalDate on, String hours) {
        return new LabStaffHrDto.CertInput(memberId, kind, on, hours == null ? null : new BigDecimal(hours),
                "교육원", "A-1", null);
    }

    // 다른 기관 직원·없는 직원은 기준도 기록도 404이고, 아무것도 저장되지 않는다
    @Test
    void profileMemberMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Member outsider = LabTestFixtures.member(em, other, "외부인", Member.Role.CAREGIVER,
                Member.MemberStatus.ACTIVE);
        assertRejected(() -> service.upsertProfile(profile(outsider.getId(), "CAREGIVER", 1980)),
                HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertRejected(() -> service.upsertProfile(profile(999_999L, "CAREGIVER", 1980)),
                HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertRejected(() -> service.upsertProfile(profile(null, "CAREGIVER", 1980)), HttpStatus.BAD_REQUEST,
                "직원");
        assertRejected(() -> service.createCert(cert(outsider.getId(), "HEALTH_CHECK", LocalDate.of(2026, 1, 5),
                null)), HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertRejected(() -> service.listCerts(outsider.getId(), null), HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertThat(profiles.count()).isZero();
        assertThat(certs.count()).isZero();

        // 다른 기관 사람은 내 기관 기준·기록을 못 지운다
        LabStaffHrDto.ProfileView mine = service.upsertProfile(profile(lee.getId(), "CAREGIVER", 1980));
        LabStaffHrDto.CertView myCert = service.createCert(cert(lee.getId(), "HEALTH_CHECK",
                LocalDate.of(2026, 1, 5), null));
        access.companyId = other.getId();
        assertRejected(() -> service.deleteProfile(mine.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        assertRejected(() -> service.deleteCert(myCert.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        assertRejected(() -> service.updateCert(myCert.id(), cert(outsider.getId(), "OTHER",
                LocalDate.of(2026, 1, 5), null)), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        assertThat(service.listProfiles()).isEmpty();
        assertThat(profiles.count()).isEqualTo(1);
    }

    // 직원마다 한 건 — 다시 저장하면 덮어쓰고, 이름이 채워지며, 직종·출생연도 규칙을 지킨다
    @Test
    void upsertByMember() {
        LabStaffHrDto.ProfileView first = service.upsertProfile(profile(lee.getId(), "CAREGIVER", 1980));
        assertThat(first.memberName()).isEqualTo("이요양");
        assertThat(first.birthYear()).isEqualTo(1980);
        assertThat(first.officeWorker()).isFalse();

        LabStaffHrDto.ProfileView second = service.upsertProfile(new LabStaffHrDto.ProfileInput(lee.getId(),
                LocalDate.of(2020, 1, 2), "OFFICE", null, null, true, null));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(profiles.count()).isEqualTo(1);
        assertThat(second.jobType()).isEqualTo("OFFICE");
        assertThat(second.officeWorker()).isTrue();
        assertThat(second.birthYear()).isNull();
        assertThat(second.licensePassedOn()).isNull();

        service.upsertProfile(profile(park.getId(), "NURSE", 2010));
        service.upsertProfile(profile(park.getId(), "NURSE", 1930));
        assertThat(service.listProfiles()).extracting(LabStaffHrDto.ProfileView::memberName)
                .containsExactly("박간호", "이요양");

        assertRejected(() -> service.upsertProfile(profile(lee.getId(), "PILOT", 1980)), HttpStatus.BAD_REQUEST,
                "직종");
        assertRejected(() -> service.upsertProfile(profile(lee.getId(), "CAREGIVER", 1929)), HttpStatus.BAD_REQUEST,
                "출생연도");
        assertRejected(() -> service.upsertProfile(profile(lee.getId(), "CAREGIVER", 2011)), HttpStatus.BAD_REQUEST,
                "출생연도");
        assertRejected(() -> service.upsertProfile(new LabStaffHrDto.ProfileInput(lee.getId(), null, "COOK", null,
                null, null, null)), HttpStatus.BAD_REQUEST, "입사일");
        assertRejected(() -> service.upsertProfile(new LabStaffHrDto.ProfileInput(lee.getId(),
                LocalDate.of(2024, 1, 1), "COOK", null, null, null, "가".repeat(201))), HttpStatus.BAD_REQUEST, "메모");
        // 거절된 저장은 기존 값을 바꾸지 않는다
        assertThat(profiles.findByCompanyIdAndMemberId(forest.getId(), lee.getId()).orElseThrow().getJobType())
                .isEqualTo("OFFICE");

        service.deleteProfile(first.id());
        assertThat(service.listProfiles()).hasSize(1);
        assertRejected(() -> service.deleteProfile(first.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
    }

    // 미래 날짜는 400, 오늘은 통과
    @Test
    void certFutureDateRejected() {
        LocalDate today = LabValidation.seoulToday();
        assertRejected(() -> service.createCert(cert(lee.getId(), "HEALTH_CHECK", today.plusDays(1), null)),
                HttpStatus.BAD_REQUEST, "오늘 이후");
        assertRejected(() -> service.createCert(cert(lee.getId(), "CONTINUING_EDU", today.plusYears(1), "8")),
                HttpStatus.BAD_REQUEST, "오늘 이후");
        assertThat(certs.count()).isZero();
        assertThat(service.createCert(cert(lee.getId(), "HEALTH_CHECK", today, null)).doneOn()).isEqualTo(today);

        // 수정으로 미래로 옮기는 것도 막는다
        LabStaffHrDto.CertView saved = service.listCerts(lee.getId(), null).get(0);
        assertRejected(() -> service.updateCert(saved.id(), cert(lee.getId(), "HEALTH_CHECK", today.plusDays(2),
                null)), HttpStatus.BAD_REQUEST, "오늘 이후");
        assertThat(certs.findById(saved.id()).orElseThrow().getDoneOn()).isEqualTo(today);
        assertRejected(() -> service.createCert(cert(lee.getId(), "HEALTH_CHECK", null, null)),
                HttpStatus.BAD_REQUEST, "이수·검진일");
        assertRejected(() -> service.createCert(cert(lee.getId(), "VACCINE", today, null)), HttpStatus.BAD_REQUEST,
                "구분");
    }

    // 보수교육은 시간이 필수(0.5~40, 소수 첫째 자리) — 다른 구분은 비워도 된다
    @Test
    void eduHoursRequired() {
        LocalDate day = LocalDate.of(2025, 6, 10);
        assertRejected(() -> service.createCert(cert(lee.getId(), "CONTINUING_EDU", day, null)),
                HttpStatus.BAD_REQUEST, "이수 시간");
        assertRejected(() -> service.createCert(cert(lee.getId(), "CONTINUING_EDU", day, "0.4")),
                HttpStatus.BAD_REQUEST, "0.5~40");
        assertRejected(() -> service.createCert(cert(lee.getId(), "CONTINUING_EDU", day, "40.5")),
                HttpStatus.BAD_REQUEST, "0.5~40");
        assertRejected(() -> service.createCert(cert(lee.getId(), "CONTINUING_EDU", day, "8.25")),
                HttpStatus.BAD_REQUEST, "소수 첫째");
        assertThat(certs.count()).isZero();

        assertThat(service.createCert(cert(lee.getId(), "CONTINUING_EDU", day, "0.5")).hours())
                .isEqualByComparingTo("0.5");
        LabStaffHrDto.CertView forty = service.createCert(cert(lee.getId(), "CONTINUING_EDU", day, "40"));
        assertThat(forty.hours()).isEqualByComparingTo("40");
        assertThat(forty.memberName()).isEqualTo("이요양");
        assertThat(service.createCert(cert(lee.getId(), "HEALTH_CHECK", day, null)).hours()).isNull();
        assertRejected(() -> service.createCert(cert(lee.getId(), "HEALTH_CHECK", day, "99")),
                HttpStatus.BAD_REQUEST, "0.5~40");

        // 수정으로 시간을 지우는 것도 막는다
        assertRejected(() -> service.updateCert(forty.id(), cert(lee.getId(), "CONTINUING_EDU", day, null)),
                HttpStatus.BAD_REQUEST, "이수 시간");
        assertThat(service.updateCert(forty.id(), cert(park.getId(), "CONTINUING_EDU", day, "8.5")).memberName())
                .isEqualTo("박간호");
    }

    // memberId·year로 거르고 둘 다 없으면 기관 전체 — 최신순, 해 경계 포함
    @Test
    void yearFilter() {
        service.createCert(cert(lee.getId(), "CONTINUING_EDU", LocalDate.of(2025, 1, 1), "4"));
        service.createCert(cert(lee.getId(), "CONTINUING_EDU", LocalDate.of(2025, 12, 31), "4"));
        service.createCert(cert(lee.getId(), "HEALTH_CHECK", LocalDate.of(2024, 12, 31), null));
        service.createCert(cert(park.getId(), "HEALTH_CHECK", LocalDate.of(2025, 5, 5), null));

        assertThat(service.listCerts(null, "2025")).extracting(LabStaffHrDto.CertView::doneOn)
                .containsExactly(LocalDate.of(2025, 12, 31), LocalDate.of(2025, 5, 5), LocalDate.of(2025, 1, 1));
        assertThat(service.listCerts(lee.getId(), "2025")).hasSize(2);
        assertThat(service.listCerts(lee.getId(), null)).extracting(LabStaffHrDto.CertView::doneOn)
                .containsExactly(LocalDate.of(2025, 12, 31), LocalDate.of(2025, 1, 1), LocalDate.of(2024, 12, 31));
        assertThat(service.listCerts(park.getId(), null)).extracting(LabStaffHrDto.CertView::memberName)
                .containsExactly("박간호");
        assertThat(service.listCerts(null, "2023")).isEmpty();

        // 조건이 없으면 해를 넘겨 전부 — 직원 인사·건강검진 기한·평가 근거가 이렇게 부른다
        assertThat(service.listCerts(null, null)).extracting(LabStaffHrDto.CertView::doneOn)
                .containsExactly(LocalDate.of(2025, 12, 31), LocalDate.of(2025, 5, 5), LocalDate.of(2025, 1, 1),
                        LocalDate.of(2024, 12, 31));
        assertRejected(() -> service.listCerts(null, "25"), HttpStatus.BAD_REQUEST, "YYYY");

        // 다른 기관은 이 기록을 보지 못한다
        access.companyId = LabTestFixtures.company(em, "다른기관").getId();
        assertThat(service.listCerts(null, "2025")).isEmpty();
        assertThat(service.listCerts(null, null)).isEmpty();
    }
}
