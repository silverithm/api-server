package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabRehabSessionDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabRehabSession;
import com.silverithm.vehicleplacementsystem.lab.repository.LabRehabSessionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능회복훈련 — 어르신 1명의 훈련 1회. 하루에 여러 번 허용하므로 쌓는 기록이다.
 *
 * <p>여러 어르신에게 같은 훈련을 한 번에 기록하는 batch는 하나의 트랜잭션이다 — 어르신 한 명이라도
 * 이 기관 소속이 아니면 404로 거절하고 아무것도 저장하지 않는다. 훈련 항목은 검증을 거친 값만 JSON으로
 * 다시 직렬화해 저장한다.
 */
@Service
public class LabRehabSessionService {

    static final int MAX_BATCH = 50;
    static final int MAX_ITEMS = 10;
    private static final int ITEM_LENGTH = 40;
    private static final int LONG_TEXT = 2000;
    private static final int REACTION_TEXT = 1000;
    private static final int NAME = 50;
    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 240;
    private static final Set<String> KINDS = Set.of("PHYSICAL_FUNCTION", "BASIC_MOTION", "ADL", "PHYSIO",
            "OCCUPATIONAL", "COGNITIVE");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabRehabSessionRepository sessions;

    public LabRehabSessionService(LabAccessService access, LabElderDirectory elders,
                                  LabRehabSessionRepository sessions) {
        this.access = access;
        this.elders = elders;
        this.sessions = sessions;
    }

    /** date(하루) 또는 month(한 달) 중 하나는 필요하다. elderlyId를 더하면 그 어르신 것만. 날짜·id 오름차순 */
    @Transactional(readOnly = true)
    public List<LabRehabSessionDto.View> list(String date, String month, Long elderlyId) {
        Long companyId = access.requireCompanyId();
        LocalDate from;
        LocalDate to;
        if (date != null) {
            from = LabValidation.date(date, "날짜");
            to = from;
        } else if (month != null) {
            YearMonth ym = LabValidation.yearMonth(month, "월");
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        } else {
            throw LabValidation.bad("날짜(YYYY-MM-DD) 또는 월(YYYY-MM)을 지정해 주세요");
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        List<LabRehabSession> rows;
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
            rows = sessions.findByCompanyIdAndElderlyIdAndSessionDateBetweenOrderBySessionDateAscIdAsc(
                    companyId, elderlyId, from, to);
        } else {
            rows = sessions.findByCompanyIdAndSessionDateBetweenOrderBySessionDateAscIdAsc(companyId, from, to);
        }
        return rows.stream().map(s -> toView(s, names.getOrDefault(s.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabRehabSessionDto.View create(LabRehabSessionDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("훈련 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        LabRehabSession session = new LabRehabSession(companyId);
        fill(session, input.elderlyId(), input.sessionDate(), input.kind(), input.minutes(), input.items(),
                input.content(), input.reaction(), input.therapistName(), input.aiAssisted());
        LabRehabSession saved = sessions.saveAndFlush(session);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public List<LabRehabSessionDto.View> createBatch(LabRehabSessionDto.BatchInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("훈련 내용을 보내 주세요");
        }
        List<Long> ids = LabValidation.idList(input.elderlyIds(), MAX_BATCH, "어르신");
        if (ids.isEmpty()) {
            throw LabValidation.bad("어르신을 한 명 이상 선택해 주세요");
        }
        // 한 번의 조회로 전원이 이 기관 어르신인지 확인한다 — 한 명이라도 아니면 아무것도 저장하지 않는다
        Map<Long, String> names = elders.requireAllInCompany(ids, companyId);
        List<LabRehabSession> toSave = new ArrayList<>();
        for (Long elderlyId : ids) {
            LabRehabSession session = new LabRehabSession(companyId);
            fill(session, elderlyId, input.sessionDate(), input.kind(), input.minutes(), input.items(),
                    input.content(), null, input.therapistName(), input.aiAssisted());
            toSave.add(session);
        }
        return sessions.saveAllAndFlush(toSave).stream()
                .map(s -> toView(s, names.getOrDefault(s.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabRehabSessionDto.View update(Long id, LabRehabSessionDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabRehabSession session = sessions.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabRehabSessionService::notFound);
        if (input == null) {
            throw LabValidation.bad("훈련 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        fill(session, input.elderlyId(), input.sessionDate(), input.kind(), input.minutes(), input.items(),
                input.content(), input.reaction(), input.therapistName(), input.aiAssisted());
        LabRehabSession saved = sessions.saveAndFlush(session);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        sessions.delete(sessions.findByIdAndCompanyId(id, companyId).orElseThrow(LabRehabSessionService::notFound));
    }

    private void fill(LabRehabSession session, Long elderlyId, LocalDate date, String kindText, Integer minutes,
                      List<String> items, String contentText, String reactionText, String therapistText,
                      Boolean ai) {
        LocalDate day = LabValidation.plausibleDate(date, "훈련 날짜");
        String kind = LabValidation.oneOf(kindText, KINDS, "훈련 종류");
        if (minutes == null) {
            throw LabValidation.bad("훈련 시간(분)을 입력해 주세요");
        }
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw LabValidation.bad("훈련 시간은 " + MIN_MINUTES + "~" + MAX_MINUTES + "분 사이여야 합니다");
        }
        String itemsJson = LabJson.write(cleanItems(items));
        String content = LabValidation.text(contentText, LONG_TEXT, "훈련 내용");
        String reaction = LabValidation.text(reactionText, REACTION_TEXT, "어르신 반응");
        String therapist = LabValidation.text(therapistText, NAME, "담당자 이름");
        session.fill(elderlyId, day, kind, minutes.shortValue(), itemsJson, content, reaction, therapist,
                Boolean.TRUE.equals(ai));
    }

    /** 공백을 정리하고 빈 항목은 버린다. 10개 초과·40자 초과는 400 — 잘라 저장하면 기록이 조용히 바뀐다 */
    private static List<String> cleanItems(List<String> items) {
        if (items == null) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String item : items) {
            String text = LabValidation.text(item, ITEM_LENGTH, "훈련 항목");
            if (text != null) {
                cleaned.add(text);
            }
        }
        if (cleaned.size() > MAX_ITEMS) {
            throw LabValidation.bad("훈련 항목은 최대 " + MAX_ITEMS + "개까지 쓸 수 있습니다");
        }
        return cleaned;
    }

    private static LabRehabSessionDto.View toView(LabRehabSession s, String elderlyName) {
        List<String> items = s.getItems() == null ? Collections.emptyList() : LabJson.readStrings(s.getItems());
        return new LabRehabSessionDto.View(s.getId(), s.getElderlyId(), elderlyName, s.getSessionDate(),
                s.getKind(), s.getMinutes(), items, s.getContent(), s.getReaction(), s.getTherapistName(),
                s.isAiAssisted(), s.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("기능회복훈련 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
