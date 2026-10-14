package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabGuardianNoticeDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabGuardianNotice;
import com.silverithm.vehicleplacementsystem.lab.repository.LabGuardianNoticeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보호자 월간 안내 — 어르신 x 월 1건(PUT은 upsert). 초안(DRAFT)을 보냄(SENT)으로 표시하고 다시 풀 수 있다.
 *
 * <p>보낸 안내는 이미 보호자에게 나간 글이라 PUT으로 조용히 고치면 기록이 실제와 어긋난다. 그래서 409로 막고,
 * 보냄 표시를 먼저 풀게(unsent) 한다. 본문·사실 묶음·받는 사람은 암호화되어 저장된다.
 */
@Service
public class LabGuardianNoticeService {

    private static final int TITLE = 100;
    private static final int BODY = 4000;
    private static final int FACTS = 8000;
    private static final int NAME = 50;
    private static final Set<String> METHODS = Set.of("SMS", "KAKAO", "PRINT", "EMAIL", "VISIT", "OTHER");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabGuardianNoticeRepository notices;
    private final TransactionTemplate tx;

    public LabGuardianNoticeService(LabAccessService access, LabElderDirectory elders,
                                    LabGuardianNoticeRepository notices, PlatformTransactionManager txManager) {
        this.access = access;
        this.elders = elders;
        this.notices = notices;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 한 달치 — 기관 전체 어르신의 안내(없는 어르신은 목록에 없다). 어르신 이름 가나다순이 아니라 등록 순 */
    @Transactional(readOnly = true)
    public List<LabGuardianNoticeDto.View> listByMonth(String month) {
        Long companyId = access.requireCompanyId();
        String ym = LabValidation.month(month, "월");
        Map<Long, String> names = elders.namesInCompany(companyId);
        return notices.findByCompanyIdAndNoticeMonthOrderByIdAsc(companyId, ym).stream()
                .map(n -> toView(n, names.getOrDefault(n.getElderlyId(), ""))).toList();
    }

    /** 어르신 x 월로 저장하거나 고친다. 이미 보낸 안내면 409 */
    public LabGuardianNoticeDto.View upsert(LabGuardianNoticeDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("안내 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        String month = LabValidation.month(input.noticeMonth(), "안내 월");
        String title = LabValidation.requiredText(input.title(), TITLE, "제목");
        String body = LabValidation.requiredText(input.body(), BODY, "안내 본문");
        String facts = LabValidation.text(input.facts(), FACTS, "사실 묶음");
        String writer = LabValidation.text(input.writerName(), NAME, "작성자 이름");
        boolean ai = Boolean.TRUE.equals(input.aiAssisted());

        LabGuardianNotice saved = LabValidation.upsertWithRetry(tx, () -> {
            LabGuardianNotice notice = notices.findByElderlyIdAndNoticeMonth(input.elderlyId(), month)
                    .orElseGet(() -> new LabGuardianNotice(companyId, input.elderlyId(), month));
            if (notice.isSent()) {
                throw new CustomException("보낸 안내입니다. 보냄 표시를 풀고 고쳐 주세요", HttpStatus.CONFLICT);
            }
            notice.fill(title, body, facts, writer, ai);
            return notices.saveAndFlush(notice);
        });
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    /** 보냄으로 표시 — 보낸 날과 방법은 필수, 받는 사람은 선택. 이미 보냄이면 보낸 정보를 새로 덮어쓴다 */
    @Transactional
    public LabGuardianNoticeDto.View markSent(Long id, LabGuardianNoticeDto.SentInput input) {
        Long companyId = access.requireCompanyId();
        LabGuardianNotice notice = notices.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabGuardianNoticeService::notFound);
        if (input == null) {
            throw LabValidation.bad("보낸 날과 방법을 보내 주세요");
        }
        LocalDate sentOn = LabValidation.plausibleDate(input.sentOn(), "보낸 날");
        String method = LabValidation.oneOf(input.method(), METHODS, "보낸 방법");
        String recipient = LabValidation.text(input.recipient(), NAME, "받는 사람");
        notice.markSent(sentOn, method, recipient);
        return toView(notices.saveAndFlush(notice), elders.nameOf(notice.getElderlyId()));
    }

    @Transactional
    public LabGuardianNoticeDto.View markUnsent(Long id) {
        Long companyId = access.requireCompanyId();
        LabGuardianNotice notice = notices.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabGuardianNoticeService::notFound);
        notice.markUnsent();
        return toView(notices.saveAndFlush(notice), elders.nameOf(notice.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        notices.delete(notices.findByIdAndCompanyId(id, companyId).orElseThrow(LabGuardianNoticeService::notFound));
    }

    private static LabGuardianNoticeDto.View toView(LabGuardianNotice n, String elderlyName) {
        return new LabGuardianNoticeDto.View(n.getId(), n.getElderlyId(), elderlyName, n.getNoticeMonth(),
                n.getTitle(), n.getBody(), n.getFacts(), n.getStatus(), n.getSentOn(), n.getMethod(),
                n.getRecipient(), n.getWriterName(), n.isAiAssisted(), n.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("보호자 안내를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
