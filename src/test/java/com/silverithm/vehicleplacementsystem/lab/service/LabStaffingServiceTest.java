package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffingDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffingMonthRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabStaffingServiceTest {

    @Autowired private LabStaffingService service;
    @Autowired private LabStaffingMonthRepository months;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Member lee;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        lee = LabTestFixtures.member(em, forest, "이요양", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        access.companyId = forest.getId();
    }

    private static LabStaffingDto.Staff staff(Long memberId, String name, String job, String hours) {
        return new LabStaffingDto.Staff(memberId, name, job, hours == null ? null : new BigDecimal(hours));
    }

    private LabStaffingDto.Input input(String month, List<String> holidays, List<LabStaffingDto.Staff> staff) {
        return new LabStaffingDto.Input(month, 22L, 440L, "미이용일 대상 2명", holidays, staff, "점검 메모", "김센터");
    }

    // 같은 달은 한 건 — 다시 저장하면 덮어쓰고, 달 조회는 없으면 404, 연도 조회는 달 순서
    @Test
    void upsertByMonth() {
        LabStaffingDto.View first = service.upsert(input("2026-03", List.of("2026-03-02"),
                List.of(staff(lee.getId(), "이요양", "CAREGIVER", "176"))));
        assertThat(first.staff()).hasSize(1);
        assertThat(first.serviceDays()).isEqualTo(22);
        assertThat(first.usersSum()).isEqualTo(440);

        LabStaffingDto.View second = service.upsert(new LabStaffingDto.Input("2026-03", 20L, 400L, null,
                List.of(), List.of(), null, null));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(months.count()).isEqualTo(1);
        assertThat(service.getByMonth("2026-03").usersSum()).isEqualTo(400);
        assertThat(service.getByMonth("2026-03").staff()).isEmpty();

        service.upsert(input("2026-01", null, null));
        service.upsert(input("2027-01", null, null));
        assertThat(service.listByYear("2026")).extracting(LabStaffingDto.View::staffingMonth)
                .containsExactly("2026-01", "2026-03");

        assertRejected(() -> service.getByMonth("2026-09"), HttpStatus.NOT_FOUND, "인력배치 기록이 없습니다");
        assertRejected(() -> service.getByMonth("2026-13"), HttpStatus.BAD_REQUEST, "YYYY-MM");
        assertRejected(() -> service.listByYear("26"), HttpStatus.BAD_REQUEST, "YYYY");

        // 다른 기관은 같은 달을 따로 갖고, 서로의 기록을 못 지운다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertRejected(() -> service.getByMonth("2026-03"), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.delete(first.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        LabStaffingDto.View mine = service.upsert(input("2026-03", null, null));
        assertThat(mine.id()).isNotEqualTo(first.id());
        access.companyId = forest.getId();
        service.delete(first.id());
        assertRejected(() -> service.getByMonth("2026-03"), HttpStatus.NOT_FOUND, null);
    }

    // 운영일수·입소자 수 범위, 직원 직종·시간·이름·인원·소속
    @Test
    void staffValidated() {
        assertRejected(() -> service.upsert(new LabStaffingDto.Input("2026-03", 0L, 10L, null, null, null, null,
                null)), HttpStatus.BAD_REQUEST, "운영일수");
        assertRejected(() -> service.upsert(new LabStaffingDto.Input("2026-03", 32L, 10L, null, null, null, null,
                null)), HttpStatus.BAD_REQUEST, "운영일수");
        assertRejected(() -> service.upsert(new LabStaffingDto.Input("2026-03", null, 10L, null, null, null, null,
                null)), HttpStatus.BAD_REQUEST, "운영일수");
        assertRejected(() -> service.upsert(new LabStaffingDto.Input("2026-03", 20L, -1L, null, null, null, null,
                null)), HttpStatus.BAD_REQUEST, "입소자 수");
        assertRejected(() -> service.upsert(new LabStaffingDto.Input("2026-03", 20L, 100_001L, null, null, null,
                null, null)), HttpStatus.BAD_REQUEST, "입소자 수");
        assertThat(service.upsert(new LabStaffingDto.Input("2026-03", 31L, 100_000L, null, null, null, null, null))
                .usersSum()).isEqualTo(100_000);

        assertRejected(() -> service.upsert(input("2026-04", null, List.of(staff(null, "김", "BOSS", "10")))),
                HttpStatus.BAD_REQUEST, "직종");
        assertRejected(() -> service.upsert(input("2026-04", null, List.of(staff(null, "김", null, "10")))),
                HttpStatus.BAD_REQUEST, "직종");
        assertRejected(() -> service.upsert(input("2026-04", null, List.of(staff(null, "김", "COOK", "-0.1")))),
                HttpStatus.BAD_REQUEST, "근무시간");
        assertRejected(() -> service.upsert(input("2026-04", null, List.of(staff(null, "김", "COOK", "400.1")))),
                HttpStatus.BAD_REQUEST, "근무시간");
        assertRejected(() -> service.upsert(input("2026-04", null, List.of(staff(null, "김", "COOK", "8.25")))),
                HttpStatus.BAD_REQUEST, "소수 첫째");
        assertRejected(() -> service.upsert(input("2026-04", null, List.of(staff(null, "김", "COOK", null)))),
                HttpStatus.BAD_REQUEST, "근무시간");
        assertRejected(() -> service.upsert(input("2026-04", null, List.of(staff(null, " ", "COOK", "8")))),
                HttpStatus.BAD_REQUEST, "직원 이름");
        assertRejected(() -> service.upsert(input("2026-04", null, List.of(staff(null, "가".repeat(51), "COOK",
                "8")))), HttpStatus.BAD_REQUEST, "50자");
        List<LabStaffingDto.Staff> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add(staff(null, "직원" + i, "OTHER", "1"));
        }
        assertRejected(() -> service.upsert(input("2026-04", null, many)), HttpStatus.BAD_REQUEST, "100명");
        assertThat(months.findByCompanyIdAndStaffingMonth(forest.getId(), "2026-04")).isEmpty();

        // 경계값은 통과하고 시간은 소수 첫째 자리로 정리된다
        LabStaffingDto.View ok = service.upsert(input("2026-04", null, List.of(
                staff(null, "가".repeat(50), "OTHER", "400"), staff(null, "김조리", "COOK", "0"),
                staff(null, "박운전", "DRIVER", "88.5"), staff(null, "최간호", "NURSE", "176.0"))));
        assertThat(ok.staff()).extracting(LabStaffingDto.Staff::hours)
                .containsExactly(new BigDecimal("400.0"), new BigDecimal("0.0"), new BigDecimal("88.5"),
                        new BigDecimal("176.0"));
        assertThat(ok.staff().get(2).jobType()).isEqualTo("DRIVER");

        // memberId는 같은 기관 직원이어야 한다 — 다른 기관·없는 id는 통째로 거절(404)
        Company other = LabTestFixtures.company(em, "다른기관");
        Member stranger = LabTestFixtures.member(em, other, "남직원", Member.Role.CAREGIVER,
                Member.MemberStatus.ACTIVE);
        assertRejected(() -> service.upsert(input("2026-05", null, List.of(
                staff(lee.getId(), "이요양", "CAREGIVER", "100"), staff(stranger.getId(), "남", "COOK", "100")))),
                HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertRejected(() -> service.upsert(input("2026-05", null, List.of(staff(999_999L, "없음", "COOK", "1")))),
                HttpStatus.NOT_FOUND, null);
        assertThat(months.findByCompanyIdAndStaffingMonth(forest.getId(), "2026-05")).isEmpty();
        assertThat(service.upsert(input("2026-05", null, List.of(staff(lee.getId(), "이요양", "CAREGIVER", "100"))))
                .staff().get(0).memberId()).isEqualTo(lee.getId());
    }

    // 공휴일은 그 달 날짜만, 31개 이하 — 중복은 합치고 날짜순으로 돌려준다
    @Test
    void holidaysOutsideMonthRejected() {
        assertRejected(() -> service.upsert(input("2026-03", List.of("2026-04-01"), null)),
                HttpStatus.BAD_REQUEST, "2026-03 안의 날짜");
        assertRejected(() -> service.upsert(input("2026-03", List.of("2025-03-01"), null)),
                HttpStatus.BAD_REQUEST, "안의 날짜");
        assertRejected(() -> service.upsert(input("2026-03", List.of("2026-03-01", "2026-02-28"), null)),
                HttpStatus.BAD_REQUEST, "안의 날짜");
        assertRejected(() -> service.upsert(input("2026-03", List.of("3월 1일"), null)),
                HttpStatus.BAD_REQUEST, "YYYY-MM-DD");
        assertRejected(() -> service.upsert(input("2026-03", List.of("2026-03-32"), null)),
                HttpStatus.BAD_REQUEST, "YYYY-MM-DD");
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            tooMany.add("2026-03-01");
        }
        assertRejected(() -> service.upsert(input("2026-03", tooMany, null)), HttpStatus.BAD_REQUEST, "31개");
        assertThat(months.count()).isZero();

        LabStaffingDto.View ok = service.upsert(input("2026-03", List.of("2026-03-02", "2026-03-01", "2026-03-02"),
                null));
        assertThat(ok.holidays()).containsExactly("2026-03-01", "2026-03-02");
        List<String> all = new ArrayList<>();
        for (int d = 1; d <= 31; d++) {
            all.add(String.format("2026-03-%02d", d));
        }
        assertThat(service.upsert(input("2026-03", all, null)).holidays()).hasSize(31);
    }

    // 직원 목록은 이름·시간이 들어 있는 JSON이라 저장된 열이 암호문이다 — 읽으면 평문으로 돌아온다
    @Test
    void staffEncryptedAtRest() {
        service.upsert(input("2026-03", List.of(), List.of(staff(lee.getId(), "이요양", "CAREGIVER", "176.5"))));
        em.flush();
        em.clear();
        assertEncryptedAtRest(jdbc, "lab_staffing_months", "staff", "이요양");
        String stored = jdbc.queryForObject("SELECT staff FROM lab_staffing_months", String.class);
        assertThat(stored).doesNotContain("CAREGIVER").doesNotContain("176.5");

        LabStaffingDto.View read = service.getByMonth("2026-03");
        assertThat(read.staff()).hasSize(1);
        assertThat(read.staff().get(0).name()).isEqualTo("이요양");
        assertThat(read.staff().get(0).memberId()).isEqualTo(lee.getId());
        assertThat(read.staff().get(0).hours()).isEqualByComparingTo("176.5");
        assertThat(read.writerName()).isEqualTo("김센터");
    }
}
