package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabIntakeDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabIntakeInquiryRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabIntakeServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final String CONSENT = "개인정보 수집·이용 동의일을 적어야 연락처를 저장할 수 있습니다";

    @Autowired private LabIntakeService service;
    @Autowired private LabIntakeInquiryRepository inquiries;
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

    private LabIntakeDto.Input input(LocalDate date, String applicant, String phone, LocalDate consentOn,
                                     String status, String declineReason, Long elderlyId) {
        return new LabIntakeDto.Input(date, applicant, "김아들", phone, "CHILD", "3", "월·수·금", "목욕과 식사 도움이 필요하심",
                "PHONE", status, declineReason, null, consentOn, DAY.plusDays(7), elderlyId, "박사회", false);
    }

    private LabIntakeDto.View simple(String applicant, String status) {
        return service.create(input(DAY, applicant, null, null, status, null, null));
    }

    @Test
    void phoneRequiresConsent() {
        assertRejected(() -> service.create(input(DAY, "박영수", "010-1234-5678", null, "NEW", null, null)),
                HttpStatus.BAD_REQUEST, CONSENT);
        assertRejected(() -> service.create(input(DAY, "박영수", " 010-1234-5678 ", null, "NEW", null, null)),
                HttpStatus.BAD_REQUEST, CONSENT);
        assertRejected(() -> service.create(input(DAY, "박영수", "0".repeat(21), DAY, "NEW", null, null)),
                HttpStatus.BAD_REQUEST, "20자");
        assertThat(inquiries.count()).isZero();

        // 연락처가 없으면 동의일 없이 저장되고, 동의일이 있으면 연락처가 저장된다
        assertThat(service.create(input(DAY, "박영수", null, null, "NEW", null, null)).consentOn()).isNull();
        assertThat(service.create(input(DAY, "박영수", "  ", null, "NEW", null, null)).contactPhone()).isNull();
        LabIntakeDto.View withConsent = service.create(input(DAY, "최영자", "010-1234-5678", DAY, "NEW", null, null));
        assertThat(withConsent.contactPhone()).isEqualTo("010-1234-5678");
        assertThat(withConsent.consentOn()).isEqualTo(DAY);

        // 수정으로 연락처를 새로 넣을 때도 같다
        LabIntakeDto.View none = simple("한영수", "NEW");
        assertRejected(() -> service.update(none.id(), input(DAY, "한영수", "010-9999-0000", null, "NEW", null, null)),
                HttpStatus.BAD_REQUEST, CONSENT);
        assertThat(service.update(none.id(), input(DAY, "한영수", "010-9999-0000", DAY, "NEW", null, null))
                .contactPhone()).isEqualTo("010-9999-0000");
    }

    @Test
    void declinedRequiresReason() {
        assertRejected(() -> service.create(input(DAY, "박영수", null, null, "DECLINED", null, null)),
                HttpStatus.BAD_REQUEST, "거절 사유");
        assertRejected(() -> service.create(input(DAY, "박영수", null, null, "DECLINED", " ", null)),
                HttpStatus.BAD_REQUEST, "거절 사유");
        assertRejected(() -> service.create(input(DAY, "박영수", null, null, "DECLINED", "NO_REASON", null)),
                HttpStatus.BAD_REQUEST, "거절 사유");
        assertThat(inquiries.count()).isZero();

        LabIntakeDto.View declined = service.create(new LabIntakeDto.Input(DAY, "박영수", null, null, null, null, null,
                null, "VISIT", "DECLINED", "CAPACITY", "정원 초과로 대기 안내", null, null, null, null, null));
        assertThat(declined.declineReason()).isEqualTo("CAPACITY");
        assertThat(declined.declineNote()).isEqualTo("정원 초과로 대기 안내");
        assertThat(declined.relation()).isNull();
        assertThat(declined.careGrade()).isNull();

        // 다른 상태로 되돌리면 거절 사유·메모를 비운다
        LabIntakeDto.View back = service.update(declined.id(), new LabIntakeDto.Input(DAY, "박영수", null, null, null,
                null, null, null, "VISIT", "WAITING", "CAPACITY", "메모", null, null, null, null, null));
        assertThat(back.status()).isEqualTo("WAITING");
        assertThat(back.declineReason()).isNull();
        assertThat(back.declineNote()).isNull();
        // 그 밖의 값 검증
        assertRejected(() -> service.create(input(DAY, "박영수", null, null, "PAUSED", null, null)),
                HttpStatus.BAD_REQUEST, "상태");
        assertRejected(() -> service.create(input(DAY, " ", null, null, "NEW", null, null)), HttpStatus.BAD_REQUEST,
                "신청 어르신 이름");
        assertRejected(() -> service.create(input(null, "박영수", null, null, "NEW", null, null)),
                HttpStatus.BAD_REQUEST, "문의일");
        assertRejected(() -> service.create(new LabIntakeDto.Input(DAY, "박영수", null, null, "FRIEND", null, null, null,
                "PHONE", "NEW", null, null, null, null, null, null, null)), HttpStatus.BAD_REQUEST, "문의자 관계");
        assertRejected(() -> service.create(new LabIntakeDto.Input(DAY, "박영수", null, null, null, "6", null, null,
                "PHONE", "NEW", null, null, null, null, null, null, null)), HttpStatus.BAD_REQUEST, "장기요양등급");
        assertRejected(() -> service.create(new LabIntakeDto.Input(DAY, "박영수", null, null, null, null, null, null,
                "FAX", "NEW", null, null, null, null, null, null, null)), HttpStatus.BAD_REQUEST, "문의 경로");
    }

    @Test
    void contractedRequiresElder() {
        assertRejected(() -> service.create(input(DAY, "김순자", null, null, "CONTRACTED", null, null)),
                HttpStatus.BAD_REQUEST, "어르신을 연결");
        assertThat(inquiries.count()).isZero();

        // 다른 기관 어르신은 연결할 수 없다
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertRejected(() -> service.create(input(DAY, "이영희", null, null, "CONTRACTED", null, stranger.getId())),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.create(input(DAY, "이영희", null, null, "WAITING", null, stranger.getId())),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertThat(inquiries.count()).isZero();

        LabIntakeDto.View contracted = service.create(input(DAY, "김순자", null, null, "CONTRACTED", null, kim.getId()));
        assertThat(contracted.elderlyId()).isEqualTo(kim.getId());
        assertThat(contracted.elderlyName()).isEqualTo("김순자");

        // 대기 중이던 상담을 계약으로 바꿀 때도 어르신을 연결해야 한다
        LabIntakeDto.View waiting = simple("박영수", "WAITING");
        assertRejected(() -> service.update(waiting.id(), input(DAY, "박영수", null, null, "CONTRACTED", null, null)),
                HttpStatus.BAD_REQUEST, "어르신을 연결");
        assertThat(service.update(waiting.id(), input(DAY, "박영수", null, null, "CONTRACTED", null, kim.getId()))
                .elderlyId()).isEqualTo(kim.getId());
        assertThat(service.list("CONTRACTED")).extracting(LabIntakeDto.View::elderlyName)
                .containsOnly("김순자");
    }

    @Test
    void namesEncryptedAtRest() {
        LabIntakeDto.View saved = service.create(input(DAY, "홍길동", "010-1234-5678", DAY, "NEW", null, null));
        em.flush();
        em.clear();

        assertEncryptedAtRest(jdbc, "lab_intake_inquiries", "applicant_name", "홍길동");
        assertEncryptedAtRest(jdbc, "lab_intake_inquiries", "contact_name", "김아들");
        assertEncryptedAtRest(jdbc, "lab_intake_inquiries", "contact_phone", "010-1234-5678");
        assertEncryptedAtRest(jdbc, "lab_intake_inquiries", "needs", "목욕과 식사");
        LabIntakeDto.View read = service.list(null).get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.applicantName()).isEqualTo("홍길동");
        assertThat(read.contactName()).isEqualTo("김아들");
        assertThat(read.contactPhone()).isEqualTo("010-1234-5678");
        assertThat(read.needs()).isEqualTo("목욕과 식사 도움이 필요하심");
        assertThat(read.relation()).isEqualTo("CHILD");
        assertThat(read.careGrade()).isEqualTo("3");
        assertThat(read.desiredDays()).isEqualTo("월·수·금");
        assertThat(read.nextContactOn()).isEqualTo(DAY.plusDays(7));
        assertThat(read.counselorName()).isEqualTo("박사회");
        // 이름이 50자를 넘으면 400
        assertRejected(() -> service.create(input(DAY, "가".repeat(51), null, null, "NEW", null, null)),
                HttpStatus.BAD_REQUEST, "50자");
    }

    @Test
    void statusFilter() {
        LabIntakeDto.View a = service.create(input(LocalDate.of(2026, 3, 1), "가나다", null, null, "NEW", null, null));
        LabIntakeDto.View b = service.create(input(LocalDate.of(2026, 3, 5), "나다라", null, null, "WAITING", null,
                null));
        LabIntakeDto.View c = service.create(input(LocalDate.of(2026, 3, 9), "다라마", null, null, "WAITING", null,
                null));
        LabIntakeDto.View d = service.create(input(LocalDate.of(2026, 3, 7), "라마바", null, null, "DECLINED", "OTHER",
                null));

        // 문의일 최신순
        assertThat(service.list(null)).extracting(LabIntakeDto.View::id).containsExactly(c.id(), d.id(), b.id(), a.id());
        assertThat(service.list("")).hasSize(4);
        assertThat(service.list("WAITING")).extracting(LabIntakeDto.View::id).containsExactly(c.id(), b.id());
        assertThat(service.list("DECLINED")).extracting(LabIntakeDto.View::id).containsExactly(d.id());
        assertThat(service.list("CONTRACTED")).isEmpty();
        assertRejected(() -> service.list("DONE"), HttpStatus.BAD_REQUEST, "상태");

        // 다른 기관에서는 보이지도 고치지도 지우지도 못한다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.list(null)).isEmpty();
        assertRejected(() -> service.delete(a.id()), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.update(a.id(), input(DAY, "가나다", null, null, "NEW", null, null)),
                HttpStatus.NOT_FOUND, null);
        access.companyId = forest.getId();
        service.delete(a.id());
        assertThat(inquiries.count()).isEqualTo(3);
    }
}
