package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.entity.VoiceMessage;
import com.silverithm.vehicleplacementsystem.lab.dto.LabGrievanceDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabGrievanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고충 요약 — 본 서비스 고충·건의함 중 '고충'(GRIEVANCE)의 요청자 기관·그 해(접수일) 건수만 센다.
 * 건의(SUGGESTION)와 다른 기관·다른 해의 글은 세지 않는다. 제목·본문·작성자·답변 글은 읽지도 내보내지도 않는다.
 */
@Service
public class LabGrievanceSummaryService {

    private final LabAccessService access;
    private final LabGrievanceRepository grievances;

    public LabGrievanceSummaryService(LabAccessService access, LabGrievanceRepository grievances) {
        this.access = access;
        this.grievances = grievances;
    }

    @Transactional(readOnly = true)
    public LabGrievanceDto.Summary summary(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        List<LabGrievanceRepository.Row> rows = grievances.findRows(companyId, VoiceMessage.VoiceType.GRIEVANCE,
                LocalDate.of(y, 1, 1).atStartOfDay(), LocalDate.of(y + 1, 1, 1).atStartOfDay());

        int anonymous = 0;
        int received = 0;
        int inReview = 0;
        int resolved = 0;
        int onHold = 0;
        List<Double> replyDays = new ArrayList<>();
        LocalDateTime oldestOpen = null;
        for (LabGrievanceRepository.Row r : rows) {
            if (r.getAnonymous()) {
                anonymous++;
            }
            switch (r.getStatus()) {
                case RECEIVED -> received++;
                case IN_REVIEW -> inReview++;
                case RESOLVED -> resolved++;
                case ON_HOLD -> onHold++;
                default -> { }
            }
            if (r.getRepliedAt() != null) {
                long minutes = Math.max(0, Duration.between(r.getCreatedAt(), r.getRepliedAt()).toMinutes());
                replyDays.add(minutes / 1440.0);
            }
            if (r.getStatus() != VoiceMessage.VoiceStatus.RESOLVED
                    && (oldestOpen == null || r.getCreatedAt().isBefore(oldestOpen))) {
                oldestOpen = r.getCreatedAt();
            }
        }
        Integer oldestOpenDays = oldestOpen == null ? null
                : (int) Math.max(0, ChronoUnit.DAYS.between(oldestOpen, LabValidation.seoulNow()));
        return new LabGrievanceDto.Summary(y, rows.size(), anonymous, received, inReview, resolved, onHold,
                replyDays.size(), median(replyDays), oldestOpenDays);
    }

    /** 중앙값 — 짝수 개면 가운데 둘의 평균, 소수 첫째 자리(반올림). 비어 있으면 null */
    static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        double mid = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        return BigDecimal.valueOf(mid).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
