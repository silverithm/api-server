package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import com.silverithm.vehicleplacementsystem.lab.dto.LabResourceLinkDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabResourceLinkRepository;

@LabJpaTest
class LabResourceLinkServiceTest {

    @Autowired private LabResourceLinkService service;
    @Autowired private LabResourceLinkRepository links;
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

    private void assertRejected(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(status);
            if (message != null) {
                assertThat(e.getMessage()).contains(message);
            }
        });
    }

    private LabResourceLinkDto.Input input(Long elderlyId, LocalDate day, String institution) {
        return new LabResourceLinkDto.Input(elderlyId, day, "DEMENTIA_CENTER", institution, "홍담당",
                "치매 검진 의뢰", "검진 일정 확정", LocalDate.of(2026, 11, 1), true, "박사회복지사", false);
    }

    @Test
    void companyWideLinkAllowsNullElder() {
        LabResourceLinkDto.View wide = service.create(input(null, LocalDate.of(2026, 10, 14), "보건소"));
        assertThat(wide.elderlyId()).isNull();
        assertThat(wide.elderlyName()).isNull();
        assertThat(wide.referralGiven()).isTrue();
        LabResourceLinkDto.View one = service.create(input(kim.getId(), LocalDate.of(2026, 10, 15), "치매안심센터"));
        assertThat(one.elderlyName()).isEqualTo("김순자");

        // 연도 목록에는 둘 다, 최신순. 어르신 목록에는 그 어르신 것만
        assertThat(service.list("2026", null)).extracting(LabResourceLinkDto.View::id)
                .containsExactly(one.id(), wide.id());
        assertThat(service.list(null, kim.getId())).extracting(LabResourceLinkDto.View::id).containsExactly(one.id());
        assertThat(service.list("2025", kim.getId())).isEmpty();
        assertThat(links.count()).isEqualTo(2);
        // 수정으로 어르신 연결을 풀어 기관 전체로 바꿀 수 있다
        assertThat(service.update(one.id(), input(null, LocalDate.of(2026, 10, 15), "치매안심센터")).elderlyId()).isNull();
        // 암호화: 담당자 이름·내용·결과
        em.flush();
        for (String column : List.of("contact_person", "content", "outcome")) {
            assertThat(jdbc.queryForObject("SELECT " + column + " FROM lab_resource_links WHERE id = ?", String.class,
                    wide.id())).as(column).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        }
        assertThat(service.list("2026", null).get(1).contactPerson()).isEqualTo("홍담당");
        assertRejected(() -> service.list(null, null), HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void elderMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");

        assertRejected(() -> service.create(input(stranger.getId(), LocalDate.of(2026, 10, 14), "보건소")),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.list(null, stranger.getId()), HttpStatus.NOT_FOUND, null);
        assertThat(links.count()).isZero();
        LabResourceLinkDto.View mine = service.create(input(kim.getId(), LocalDate.of(2026, 10, 14), "보건소"));
        assertRejected(() -> service.update(mine.id(), input(stranger.getId(), LocalDate.of(2026, 10, 14), "보건소")),
                HttpStatus.NOT_FOUND, null);
        access.companyId = other.getId();
        assertRejected(() -> service.delete(mine.id()), HttpStatus.NOT_FOUND, null);
        assertThat(service.list("2026", null)).isEmpty();
    }

    @Test
    void institutionRequired() {
        assertRejected(() -> service.create(input(null, LocalDate.of(2026, 10, 14), null)), HttpStatus.BAD_REQUEST,
                "기관 이름");
        assertRejected(() -> service.create(input(null, LocalDate.of(2026, 10, 14), "   ")), HttpStatus.BAD_REQUEST,
                "기관 이름");
        assertRejected(() -> service.create(input(null, LocalDate.of(2026, 10, 14), "가".repeat(101))),
                HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.create(new LabResourceLinkDto.Input(null, LocalDate.of(2026, 10, 14), "MEDICAL",
                "보건소", null, " ", null, null, null, null, null)), HttpStatus.BAD_REQUEST, "연계 내용");
        assertRejected(() -> service.create(new LabResourceLinkDto.Input(null, LocalDate.of(2026, 10, 14), "SPACE",
                "보건소", null, "내용", null, null, null, null, null)), HttpStatus.BAD_REQUEST, null);
        assertThat(links.count()).isZero();
        // 선택 값은 비어도 되고 연계기록지는 기본 false
        LabResourceLinkDto.View minimal = service.create(new LabResourceLinkDto.Input(null, LocalDate.of(2026, 10, 14),
                "MEDICAL", "보건소", null, "내용", null, null, null, null, null));
        assertThat(minimal.referralGiven()).isFalse();
        assertThat(minimal.followUpOn()).isNull();
    }
}
