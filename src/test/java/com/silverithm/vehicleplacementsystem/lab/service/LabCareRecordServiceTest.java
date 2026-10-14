package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareRecordDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCareRecordRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
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
                "혈압 140/90, 어지러움 호소", rawNote, "박요양", true);
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
                "가".repeat(201), null, null, null, null, null, null, null, null)))
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
}
