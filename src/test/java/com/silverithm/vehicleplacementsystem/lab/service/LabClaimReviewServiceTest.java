package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabClaimReviewDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabClaimReviewRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabClaimReviewServiceTest {

    private static final String MONTH = "2026-03";

    @Autowired private LabClaimReviewService service;
    @Autowired private LabClaimReviewRepository reviews;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly park;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        park = LabTestFixtures.elder(em, forest, "박영수");
        access.companyId = forest.getId();
    }

    private LabClaimReviewDto.Input row(Elderly elder, Integer claimed, Integer attended, Integer billed, String status,
                                        String note) {
        return new LabClaimReviewDto.Input(elder.getId(), MONTH, claimed, attended, billed, "h8to10", status, note,
                "박사회복지사");
    }

    @Test
    void statusComputedMatch() {
        assertThat(service.upsert(row(kim, 20, 20, null, null, null)).status()).isEqualTo("MATCH");
        assertThat(service.upsert(row(park, 18, 18, 18, null, null)).status()).isEqualTo("MATCH");
        // 클라이언트가 MISMATCH라고 보내도 서버 계산이 우선한다
        assertThat(service.upsert(row(kim, 20, 20, 20, "MISMATCH", null)).status()).isEqualTo("MATCH");
        // 0일 청구·0일 출석도 일치
        assertThat(service.upsert(row(park, 0, 0, null, null, null)).status()).isEqualTo("MATCH");
        // 사유 없는 RESOLVED는 근거가 없으니 계산 결과를 따른다
        assertThat(service.upsert(row(kim, 20, 20, null, "RESOLVED", null)).status()).isEqualTo("MATCH");
        assertThat(service.upsert(row(kim, 20, 20, null, "RESOLVED", "  ")).status()).isEqualTo("MATCH");
    }

    @Test
    void statusComputedMismatch() {
        assertThat(service.upsert(row(kim, 22, 20, null, null, null)).status()).isEqualTo("MISMATCH");
        // 청구 = 출석이어도 청구서 일수가 다르면 불일치
        assertThat(service.upsert(row(park, 20, 20, 19, "MATCH", null)).status()).isEqualTo("MISMATCH");
        assertThat(service.upsert(row(kim, 20, 21, null, null, null)).status()).isEqualTo("MISMATCH");
        // 사유 없는 RESOLVED는 불일치 그대로
        assertThat(service.upsert(row(kim, 22, 20, null, "RESOLVED", null)).status()).isEqualTo("MISMATCH");
        // 값 검증
        assertRejected(() -> service.upsert(row(kim, 32, 20, null, null, null)), HttpStatus.BAD_REQUEST, "0~31");
        assertRejected(() -> service.upsert(row(kim, 20, -1, null, null, null)), HttpStatus.BAD_REQUEST, "0~31");
        assertRejected(() -> service.upsert(row(kim, 20, 20, 32, null, null)), HttpStatus.BAD_REQUEST, "0~31");
        assertRejected(() -> service.upsert(row(kim, null, 20, null, null, null)), HttpStatus.BAD_REQUEST, "청구 일수");
        assertRejected(() -> service.upsert(row(kim, 20, 20, null, "DONE", null)), HttpStatus.BAD_REQUEST, "상태");
        assertRejected(() -> service.upsert(new LabClaimReviewDto.Input(kim.getId(), MONTH, 20, 20, null, "h1to2", null,
                null, null)), HttpStatus.BAD_REQUEST, "시간 구간");
        assertRejected(() -> service.upsert(new LabClaimReviewDto.Input(kim.getId(), "2026-13", 20, 20, null, null, null,
                null, null)), HttpStatus.BAD_REQUEST, "YYYY-MM");
        assertRejected(() -> service.upsert(row(kim, 20, 20, null, null, "가".repeat(501))), HttpStatus.BAD_REQUEST,
                "500자");
        assertRejected(() -> service.upsert(new LabClaimReviewDto.Input(null, MONTH, 20, 20, null, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "어르신");
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertRejected(() -> service.upsert(row(stranger, 20, 20, null, null, null)), HttpStatus.NOT_FOUND,
                "어르신을 찾을 수 없습니다");
        assertThat(reviews.count()).isEqualTo(2);
    }

    @Test
    void resolvedKeptWithNote() {
        LabClaimReviewDto.View resolved = service.upsert(row(kim, 22, 20, null, "RESOLVED",
                "결석일 2일을 공단에 정정 요청함"));
        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.note()).isEqualTo("결석일 2일을 공단에 정정 요청함");
        // 다시 저장해도 사유와 함께 RESOLVED로 보내면 유지, 사유를 지우면 계산 결과로 돌아간다
        assertThat(service.upsert(row(kim, 22, 20, null, "RESOLVED", "정정 완료")).status()).isEqualTo("RESOLVED");
        assertThat(service.upsert(row(kim, 22, 20, null, null, "정정 완료")).status()).isEqualTo("MISMATCH");
        assertThat(service.upsert(row(kim, 22, 20, null, "RESOLVED", null)).status()).isEqualTo("MISMATCH");
        // 일괄 저장에서도 같은 규칙
        List<LabClaimReviewDto.View> saved = service.batch(new LabClaimReviewDto.BatchInput(MONTH, List.of(
                row(kim, 22, 20, null, "RESOLVED", "정정 완료"), row(park, 10, 10, null, null, null))));
        assertThat(saved).extracting(LabClaimReviewDto.View::status).containsExactly("RESOLVED", "MATCH");
    }

    @Test
    void batchAllOrNothing() {
        // 이미 있는 행 하나
        service.upsert(row(kim, 20, 20, null, null, null));

        // 한 행이라도 잘못되면(마지막 행이 31일 초과) 앞 행들도 저장되지 않고, 기존 행도 바뀌지 않는다
        assertRejected(() -> service.batch(new LabClaimReviewDto.BatchInput(MONTH, List.of(
                row(kim, 25, 20, null, null, null), row(park, 10, 10, null, null, null),
                row(kim, 40, 20, null, null, null)))), HttpStatus.BAD_REQUEST, null);
        assertThat(reviews.count()).isEqualTo(1);
        assertThat(service.list(MONTH).get(0).claimedDays()).isEqualTo(20);

        // 같은 어르신 두 행·다른 청구월 행·다른 기관 어르신도 전체 거절
        assertRejected(() -> service.batch(new LabClaimReviewDto.BatchInput(MONTH, List.of(
                row(park, 10, 10, null, null, null), row(park, 11, 11, null, null, null)))), HttpStatus.BAD_REQUEST,
                "두 행");
        assertRejected(() -> service.batch(new LabClaimReviewDto.BatchInput(MONTH, List.of(
                row(park, 10, 10, null, null, null), new LabClaimReviewDto.Input(kim.getId(), "2026-04", 1, 1, null, null,
                        null, null, null)))), HttpStatus.BAD_REQUEST, "청구월");
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertRejected(() -> service.batch(new LabClaimReviewDto.BatchInput(MONTH, List.of(
                row(park, 10, 10, null, null, null), row(stranger, 10, 10, null, null, null)))), HttpStatus.NOT_FOUND,
                "어르신을 찾을 수 없습니다");
        assertThat(reviews.count()).isEqualTo(1);

        // 빈 목록·200행 초과·월 형식 오류
        assertRejected(() -> service.batch(new LabClaimReviewDto.BatchInput(MONTH, List.of())), HttpStatus.BAD_REQUEST,
                "저장할 행");
        assertRejected(() -> service.batch(new LabClaimReviewDto.BatchInput(MONTH, null)), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.batch(new LabClaimReviewDto.BatchInput("2026/03", List.of(row(park, 1, 1, null, null,
                null)))), HttpStatus.BAD_REQUEST, "YYYY-MM");
        List<LabClaimReviewDto.Input> tooMany = new ArrayList<>();
        for (long i = 0; i < 201; i++) {
            tooMany.add(new LabClaimReviewDto.Input(i + 1, MONTH, 1, 1, null, null, null, null, null));
        }
        assertRejected(() -> service.batch(new LabClaimReviewDto.BatchInput(MONTH, tooMany)), HttpStatus.BAD_REQUEST,
                "200행");
        assertThat(reviews.count()).isEqualTo(1);

        // 모두 올바르면 전부 저장하고 입력 순서대로 돌려준다. 기존 행은 덮어쓴다. 행의 청구월은 비워도 된다
        List<LabClaimReviewDto.View> saved = service.batch(new LabClaimReviewDto.BatchInput(MONTH, List.of(
                new LabClaimReviewDto.Input(park.getId(), null, 10, 9, null, "over13", null, null, null),
                row(kim, 25, 20, null, null, null))));
        assertThat(saved).extracting(LabClaimReviewDto.View::elderlyName).containsExactly("박영수", "김순자");
        assertThat(saved).extracting(LabClaimReviewDto.View::status).containsExactly("MISMATCH", "MISMATCH");
        assertThat(saved.get(0).claimMonth()).isEqualTo(MONTH);
        assertThat(saved.get(0).claimedBand()).isEqualTo("over13");
        assertThat(reviews.count()).isEqualTo(2);
        assertThat(service.list(MONTH).stream().filter(v -> v.elderlyId().equals(kim.getId())).findFirst().orElseThrow()
                .claimedDays()).isEqualTo(25);
    }

    @Test
    void upsertByElderAndMonth() {
        LabClaimReviewDto.View first = service.upsert(row(kim, 20, 20, null, null, null));
        LabClaimReviewDto.View again = service.upsert(new LabClaimReviewDto.Input(kim.getId(), MONTH, 21, 20, 20, "h6to8",
                null, "확인 필요", "최사회복지사"));

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(reviews.count()).isEqualTo(1);
        assertThat(again.claimedDays()).isEqualTo(21);
        assertThat(again.billedDays()).isEqualTo(20);
        assertThat(again.claimedBand()).isEqualTo("h6to8");
        assertThat(again.reviewerName()).isEqualTo("최사회복지사");
        assertThat(again.elderlyName()).isEqualTo("김순자");
        // 다른 달·다른 어르신은 새 행
        service.upsert(new LabClaimReviewDto.Input(kim.getId(), "2026-04", 20, 20, null, null, null, null, null));
        service.upsert(row(park, 20, 20, null, null, null));
        assertThat(reviews.count()).isEqualTo(3);
        assertThat(service.list(MONTH)).extracting(LabClaimReviewDto.View::elderlyId)
                .containsExactly(kim.getId(), park.getId());
        assertThat(service.list("2026-05")).isEmpty();
        assertRejected(() -> service.list(null), HttpStatus.BAD_REQUEST, "YYYY-MM");

        // 다른 기관은 내 행을 보지도 지우지도 못한다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.list(MONTH)).isEmpty();
        assertRejected(() -> service.delete(first.id()), HttpStatus.NOT_FOUND, null);
        access.companyId = forest.getId();
        service.delete(first.id());
        assertThat(reviews.count()).isEqualTo(2);
    }
}
