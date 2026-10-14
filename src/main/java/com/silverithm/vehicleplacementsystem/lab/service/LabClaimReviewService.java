package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabClaimReviewDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabClaimReview;
import com.silverithm.vehicleplacementsystem.lab.repository.LabClaimReviewRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공단 청구 대조 결과.
 *
 * <p>어르신 x 청구월 1건을 덮어쓴다. 상태는 서버가 정한다 — 공단 청구 일수가 출석 일수와 같고(본인부담금 청구서 일수가
 * 있으면 그것과도 같으면) MATCH, 아니면 MISMATCH. 단 직원이 차이를 확인하고 사유(note)를 적어 RESOLVED로 보냈으면 그대로 둔다.
 * 사유 없는 RESOLVED는 "확인했다"는 근거가 없으므로 계산 결과를 따른다. 일괄 저장(batch)은 200행까지이고
 * 한 행이라도 잘못되면 아무것도 저장하지 않는다(검증을 전부 마친 뒤 한 트랜잭션으로 저장).
 */
@Service
public class LabClaimReviewService {

    static final int MAX_BATCH = 200;
    private static final int NOTE = 500;
    private static final int NAME = 50;
    private static final int MAX_DAYS = 31;
    private static final Set<String> BANDS = Set.of("h3to6", "h6to8", "h8to10", "h10to13", "over13", "under3");
    private static final Set<String> INPUT_STATUSES = Set.of(LabClaimReview.MATCH, LabClaimReview.MISMATCH,
            LabClaimReview.RESOLVED);

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabClaimReviewRepository reviews;
    private final TransactionTemplate tx;

    public LabClaimReviewService(LabAccessService access, LabElderDirectory elders, LabClaimReviewRepository reviews,
                                 PlatformTransactionManager txManager) {
        this.access = access;
        this.elders = elders;
        this.reviews = reviews;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 한 달치(month 필수), 어르신 id 순 */
    @Transactional(readOnly = true)
    public List<LabClaimReviewDto.View> list(String month) {
        Long companyId = access.requireCompanyId();
        String m = LabValidation.month(month, "청구월");
        Map<Long, String> names = elders.namesInCompany(companyId);
        return reviews.findByCompanyIdAndClaimMonthOrderByElderlyIdAscIdAsc(companyId, m).stream()
                .map(r -> toView(r, names.getOrDefault(r.getElderlyId(), ""))).toList();
    }

    /** elderlyId + claimMonth로 덮어쓴다 */
    public LabClaimReviewDto.View upsert(LabClaimReviewDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("청구 대조 내용을 보내 주세요");
        }
        Row row = row(input, null);
        elders.requireInCompany(row.elderlyId(), companyId);
        List<LabClaimReview> saved = LabValidation.upsertWithRetry(tx, () -> save(companyId, List.of(row)));
        return toView(saved.get(0), elders.nameOf(row.elderlyId()));
    }

    /** month의 여러 어르신을 한 번에 — 전부 아니면 없음. 응답은 입력 순서 */
    public List<LabClaimReviewDto.View> batch(LabClaimReviewDto.BatchInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("청구 대조 내용을 보내 주세요");
        }
        String month = LabValidation.month(input.month(), "청구월");
        List<LabClaimReviewDto.Input> inputs = input.rows();
        if (inputs == null || inputs.isEmpty()) {
            throw LabValidation.bad("저장할 행이 없습니다");
        }
        if (inputs.size() > MAX_BATCH) {
            throw LabValidation.bad("한 번에 최대 " + MAX_BATCH + "행까지 저장할 수 있습니다");
        }
        List<Row> rows = new ArrayList<>(inputs.size());
        Set<Long> seen = new HashSet<>();
        for (LabClaimReviewDto.Input in : inputs) {
            if (in == null) {
                throw LabValidation.bad("행 목록에 올바르지 않은 값이 있습니다");
            }
            Row row = row(in, month);
            if (!seen.add(row.elderlyId())) {
                throw LabValidation.bad("같은 어르신이 두 행에 있습니다");
            }
            rows.add(row);
        }
        // 소속 확인은 쿼리 한 번으로 — 하나라도 다른 기관 어르신이면 404로 전체 거절
        Map<Long, String> names = elders.requireAllInCompany(seen, companyId);
        List<LabClaimReview> saved = LabValidation.upsertWithRetry(tx, () -> save(companyId, rows));
        return saved.stream().map(r -> toView(r, names.getOrDefault(r.getElderlyId(), ""))).toList();
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        reviews.delete(reviews.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("청구 대조 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    /** 한 트랜잭션 안에서 행들을 모두 덮어쓴다 — 중간에 터지면 전부 되돌아간다 */
    private List<LabClaimReview> save(Long companyId, List<Row> rows) {
        Map<Long, LabClaimReview> existing;
        if (rows.size() == 1) {
            Row only = rows.get(0);
            Optional<LabClaimReview> found = reviews.findByElderlyIdAndClaimMonth(only.elderlyId(), only.month());
            existing = found.map(r -> Map.of(r.getElderlyId(), r)).orElse(Map.of());
        } else {
            // 일괄 저장은 한 달치라 달 목록을 한 번에 읽어 행마다 조회하지 않는다
            existing = reviews.findByCompanyIdAndClaimMonthOrderByElderlyIdAscIdAsc(companyId, rows.get(0).month())
                    .stream().collect(Collectors.toMap(LabClaimReview::getElderlyId, Function.identity()));
        }
        List<LabClaimReview> saved = new ArrayList<>(rows.size());
        for (Row row : rows) {
            LabClaimReview review = existing.get(row.elderlyId());
            if (review == null) {
                review = new LabClaimReview(companyId, row.elderlyId(), row.month());
            }
            review.fill(row.claimed(), row.attended(), row.billed(), row.band(), row.status(), row.note(),
                    row.reviewer());
            saved.add(reviews.save(review));
        }
        reviews.flush();
        return saved;
    }

    private record Row(Long elderlyId, String month, short claimed, short attended, Short billed, String band,
                       String status, String note, String reviewer) {
    }

    /** 입력 한 행을 검증하고 상태를 계산한다. defaultMonth가 있으면 claimMonth를 비워도 되고, 다르면 400 */
    private static Row row(LabClaimReviewDto.Input in, String defaultMonth) {
        if (in.elderlyId() == null) {
            throw LabValidation.bad("어르신을 선택해 주세요");
        }
        String month;
        if (defaultMonth != null && (in.claimMonth() == null || in.claimMonth().isBlank())) {
            month = defaultMonth;
        } else {
            month = LabValidation.month(in.claimMonth(), "청구월");
            if (defaultMonth != null && !defaultMonth.equals(month)) {
                throw LabValidation.bad("행의 청구월이 일괄 저장의 월과 다릅니다");
            }
        }
        short claimed = days(in.claimedDays(), "공단 청구 일수");
        short attended = days(in.attendedDays(), "출석 일수");
        Short billed = in.billedDays() == null ? null : days(in.billedDays(), "본인부담금 청구서 일수");
        String band = in.claimedBand() == null || in.claimedBand().isBlank() ? null
                : LabValidation.oneOf(in.claimedBand(), BANDS, "청구 시간 구간");
        String note = LabValidation.text(in.note(), NOTE, "확인 사유");
        String reviewer = LabValidation.text(in.reviewerName(), NAME, "확인자 이름");
        String requested = in.status() == null || in.status().isBlank() ? null
                : LabValidation.oneOf(in.status(), INPUT_STATUSES, "상태");
        return new Row(in.elderlyId(), month, claimed, attended, billed, band,
                computeStatus(claimed, attended, billed, requested, note), note, reviewer);
    }

    /** 청구 = 출석(= 청구서 일수, 있으면)이면 MATCH, 아니면 MISMATCH. 사유와 함께 RESOLVED로 보낸 것은 유지 */
    static String computeStatus(int claimed, int attended, Short billed, String requested, String note) {
        if (LabClaimReview.RESOLVED.equals(requested) && note != null) {
            return LabClaimReview.RESOLVED;
        }
        boolean same = claimed == attended && (billed == null || billed == claimed);
        return same ? LabClaimReview.MATCH : LabClaimReview.MISMATCH;
    }

    private static short days(Integer value, String label) {
        if (value == null) {
            throw LabValidation.bad(label + "을(를) 입력해 주세요");
        }
        return (short) LabValidation.longInRange(value.longValue(), 0, MAX_DAYS, label);
    }

    private static LabClaimReviewDto.View toView(LabClaimReview r, String elderlyName) {
        return new LabClaimReviewDto.View(r.getId(), r.getElderlyId(), elderlyName, r.getClaimMonth(),
                r.getClaimedDays(), r.getAttendedDays(), r.getBilledDays() == null ? null
                : r.getBilledDays().intValue(), r.getClaimedBand(), r.getStatus(), r.getNote(),
                r.getReviewerName(), r.getUpdatedAt());
    }
}
