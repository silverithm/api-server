package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ExternalNoticeDTO;
import com.silverithm.vehicleplacementsystem.entity.ExternalNotice;
import com.silverithm.vehicleplacementsystem.repository.ExternalNoticeRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 노인장기요양보험(longtermcare.or.kr) 사이트의 요양기관 실무 관련 게시판을 주기적으로 수집한다.
 *
 * <p>서버 렌더링 HTML을 jsoup으로 파싱하며, 게시판별 목록 페이지 1페이지(최신 30건 + 상단 고정 공지)만 확인한다.
 * source(게시판) + external_id(boardId) 조합으로 이미 저장된 글은 건너뛴다. 게시판 하나가 실패해도
 * 나머지 게시판 수집은 계속 진행한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalNoticeCrawlerService {

    /** 수집 대상 게시판 - 노인장기요양보험 사이트(longtermcare.or.kr)의 요양기관 실무 관련 게시판 4종. */
    public enum LtcBoard {
        LTC_NOTICE("공지사항", "B0163", "npe0000002786"),
        LTC_LAW("법령자료실", "B0018", "npe0000002791"),
        LTC_EVAL("평가 매뉴얼", "B0153", "npe0000002804"),
        LTC_EDU("기관종사자 교육", "B0069", "npe0000002810");

        private final String label;
        private final String bKey;
        private final String menuId;

        LtcBoard(String label, String bKey, String menuId) {
            this.label = label;
            this.bKey = bKey;
            this.menuId = menuId;
        }

        /** 저장/조회에 쓰이는 source 코드 (enum 상수명 그대로 사용). */
        public String source() {
            return name();
        }

        public String label() {
            return label;
        }

        public String bKey() {
            return bKey;
        }

        public String menuId() {
            return menuId;
        }

        /** source 코드를 표시명으로 변환한다. 알 수 없는 source는 "기타"를 반환한다. */
        public static String labelFor(String source) {
            for (LtcBoard board : values()) {
                if (board.source().equals(source)) {
                    return board.label();
                }
            }
            return "기타";
        }
    }

    private static final String LIST_URL_TEMPLATE =
            "https://www.longtermcare.or.kr/npbs/cms/board/board/Board.jsp"
                    + "?searchType=ALL&searchWord=&pageSize=30&pageNum=1&list_show_answer=N&communityKey=%s";

    private static final String DETAIL_URL_TEMPLATE =
            "https://www.longtermcare.or.kr/npbs/d/m/000/moveBoardView"
                    + "?menuId=%s&bKey=%s&search_boardId=%s";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_TITLE_LENGTH = 500;

    private static final Pattern BOARD_ID_PATTERN = Pattern.compile("boardId=(\\d+)");
    private static final DateTimeFormatter POSTED_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExternalNoticeRepository externalNoticeRepository;

    @Transactional(readOnly = true)
    public Page<ExternalNoticeDTO> getExternalNotices(String source, Pageable pageable) {
        Page<ExternalNotice> page = StringUtils.hasText(source)
                ? externalNoticeRepository.findAllBySourceOrderByPostedDateDesc(source, pageable)
                : externalNoticeRepository.findAllByOrderByPostedDateDesc(pageable);
        return page.map(ExternalNoticeDTO::from);
    }

    /** 게시판 4종을 순회 수집해 신규 공지만 저장한다. 게시판 하나가 실패해도 나머지는 계속 수집한다. */
    public void collect() {
        int totalSaved = 0;
        for (LtcBoard board : LtcBoard.values()) {
            try {
                int saved = collectBoard(board);
                totalSaved += saved;
                log.info("[ExternalNotice] {}({}) 수집 완료: 신규 {}건", board.source(), board.label(), saved);
            } catch (Exception e) {
                log.warn("[ExternalNotice] {}({}) 수집 실패: {}", board.source(), board.label(), e.getMessage());
            }
        }
        log.info("[ExternalNotice] 전체 수집 완료: 신규 {}건", totalSaved);
    }

    private int collectBoard(LtcBoard board) throws Exception {
        String listUrl = String.format(LIST_URL_TEMPLATE, board.bKey());
        Document doc = Jsoup.connect(listUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MILLIS)
                .get();

        Elements titleCells = doc.select("td[headers=board_title]");
        int saved = 0;
        for (Element titleCell : titleCells) {
            try {
                if (collectRow(board, titleCell)) {
                    saved++;
                }
            } catch (Exception e) {
                log.warn("[ExternalNotice] {} 행 파싱 실패: {}", board.source(), e.getMessage());
            }
        }
        return saved;
    }

    private boolean collectRow(LtcBoard board, Element titleCell) {
        Element link = titleCell.selectFirst("a[href*=boardId=]");
        if (link == null) {
            return false;
        }

        Matcher matcher = BOARD_ID_PATTERN.matcher(link.attr("href"));
        if (!matcher.find()) {
            return false;
        }
        String boardId = matcher.group(1);

        if (externalNoticeRepository.existsBySourceAndExternalId(board.source(), boardId)) {
            return false;
        }

        String title = link.text().trim();
        if (title.isEmpty()) {
            return false;
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }

        LocalDate postedDate = parsePostedDate(titleCell);
        String url = String.format(DETAIL_URL_TEMPLATE, board.menuId(), board.bKey(), boardId);

        externalNoticeRepository.save(ExternalNotice.builder()
                .source(board.source())
                .externalId(boardId)
                .title(title)
                .url(url)
                .postedDate(postedDate)
                .build());
        return true;
    }

    private LocalDate parsePostedDate(Element titleCell) {
        Element row = titleCell.closest("tr");
        if (row == null) {
            return null;
        }
        Element dateCell = row.selectFirst("td[headers=board_create]");
        if (dateCell == null) {
            return null;
        }
        String text = dateCell.text().trim();
        try {
            return LocalDate.parse(text, POSTED_DATE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}
