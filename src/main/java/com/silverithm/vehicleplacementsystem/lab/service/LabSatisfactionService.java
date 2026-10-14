package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabSatisfactionDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabSatisfactionResponse;
import com.silverithm.vehicleplacementsystem.lab.repository.LabSatisfactionResponseRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 급여 만족도 응답(평가 36) — 식사·프로그램·이동·전반 네 종류, 문항별 1~5점.
 *
 * <p>어르신을 고르면 같은 기관 소속이어야 하고, 고르지 않으면 익명 응답이다. 응답일은 오늘 이후일 수 없다.
 * 조회는 기간(from·to)이 필수이고 1년을 넘길 수 없다 — 응답이 쌓여도 한 번에 읽는 양이 한정되게 한다.
 * 의견은 암호화한다.
 */
@Service
public class LabSatisfactionService {

    static final Set<String> KINDS = Set.of("MEAL", "PROGRAM", "TRANSPORT", "GENERAL");
    static final Set<String> RESPONDENTS = Set.of("SELF", "GUARDIAN");
    static final int MAX_QUESTIONS = 20;
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{2,30}$");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabSatisfactionResponseRepository responses;

    public LabSatisfactionService(LabAccessService access, LabElderDirectory elders,
                                  LabSatisfactionResponseRepository responses) {
        this.access = access;
        this.elders = elders;
        this.responses = responses;
    }

    /** kind는 생략할 수 있고, from·to는 필수(양 끝 포함, 최대 1년). 응답일 최신순 */
    @Transactional(readOnly = true)
    public List<LabSatisfactionDto.View> list(String kind, String from, String to) {
        Long companyId = access.requireCompanyId();
        String wanted = kind == null || kind.isBlank() ? null : LabValidation.oneOf(kind, KINDS, "종류");
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw LabValidation.bad("조회 기간(from, to)을 모두 보내 주세요");
        }
        LocalDate start = LabValidation.date(from, "조회 시작일");
        LocalDate end = LabValidation.date(to, "조회 종료일");
        if (end.isBefore(start)) {
            throw LabValidation.bad("조회 종료일이 시작일보다 앞일 수 없습니다");
        }
        if (end.isAfter(start.plusYears(1))) {
            throw LabValidation.bad("조회 기간은 최대 1년입니다");
        }
        List<LabSatisfactionResponse> rows = wanted == null
                ? responses.findByCompanyIdAndSurveyDateBetweenOrderBySurveyDateDescIdDesc(companyId, start, end)
                : responses.findByCompanyIdAndKindAndSurveyDateBetweenOrderBySurveyDateDescIdDesc(companyId, wanted,
                        start, end);
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream().map(r -> toView(r, nameOf(r.getElderlyId(), names))).toList();
    }

    @Transactional
    public LabSatisfactionDto.View create(LabSatisfactionDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabSatisfactionResponse response = new LabSatisfactionResponse(companyId);
        apply(response, input, companyId);
        return save(response);
    }

    @Transactional
    public LabSatisfactionDto.View update(Long id, LabSatisfactionDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabSatisfactionResponse response = responses.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabSatisfactionService::notFound);
        apply(response, input, companyId);
        return save(response);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        responses.delete(responses.findByIdAndCompanyId(id, companyId).orElseThrow(LabSatisfactionService::notFound));
    }

    private LabSatisfactionDto.View save(LabSatisfactionResponse response) {
        LabSatisfactionResponse saved = responses.saveAndFlush(response);
        return toView(saved, saved.getElderlyId() == null ? null : elders.nameOf(saved.getElderlyId()));
    }

    private void apply(LabSatisfactionResponse response, LabSatisfactionDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("만족도 응답을 보내 주세요");
        }
        LocalDate surveyDate = LabValidation.plausibleDate(input.surveyDate(), "응답일");
        if (surveyDate.isAfter(LabValidation.seoulToday())) {
            throw LabValidation.bad("응답일은 오늘 이후일 수 없습니다");
        }
        String kind = LabValidation.oneOf(input.kind(), KINDS, "종류");
        if (input.elderlyId() != null) {
            elders.requireInCompany(input.elderlyId(), companyId);
        }
        String respondent = LabValidation.oneOf(input.respondent(), RESPONDENTS, "응답자");
        String scores = LabJson.write(scores(input.scores()));
        String comment = LabValidation.text(input.comment(), 1000, "의견");
        String interviewer = LabValidation.text(input.interviewerName(), 50, "면담자 이름");
        response.fill(surveyDate, kind, input.elderlyId(), respondent, scores, comment, interviewer);
    }

    /** 문항 1~20개, 코드는 대문자·숫자·밑줄 2~30자, 점수는 1~5 */
    private static Map<String, Integer> scores(Map<String, Integer> input) {
        if (input == null || input.isEmpty()) {
            throw LabValidation.bad("문항 점수를 하나 이상 보내 주세요");
        }
        if (input.size() > MAX_QUESTIONS) {
            throw LabValidation.bad("문항은 최대 " + MAX_QUESTIONS + "개까지 쓸 수 있습니다");
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : input.entrySet()) {
            String code = e.getKey();
            if (code == null || !CODE.matcher(code).matches()) {
                throw LabValidation.bad("문항 코드는 대문자·숫자·밑줄 2~30자여야 합니다: " + code);
            }
            Integer score = e.getValue();
            if (score == null || score < 1 || score > 5) {
                throw LabValidation.bad("문항 점수는 1~5점이어야 합니다: " + code);
            }
            result.put(code, score);
        }
        return result;
    }

    private static String nameOf(Long elderlyId, Map<Long, String> names) {
        return elderlyId == null ? null : names.getOrDefault(elderlyId, "");
    }

    private static LabSatisfactionDto.View toView(LabSatisfactionResponse r, String elderlyName) {
        return new LabSatisfactionDto.View(r.getId(), r.getSurveyDate(), r.getKind(), r.getElderlyId(), elderlyName,
                r.getRespondent(), LabJson.readSatisfactionScores(r.getScores()), r.getComment(),
                r.getInterviewerName(), r.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("만족도 응답을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
