package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.NewsArticleDTO;
import com.silverithm.vehicleplacementsystem.entity.NewsArticle;
import com.silverithm.vehicleplacementsystem.entity.NewsCategory;
import com.silverithm.vehicleplacementsystem.repository.NewsArticleRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * 케어브이 광장 - 요양 소식 수집/조회.
 * 네이버 뉴스 검색 Open API(developers.naver.com 애플리케이션 키 필요)로
 * 카테고리별 키워드를 검색해 적재한다. 키가 없으면 수집을 건너뛴다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private static final int MAX_ITEMS_PER_QUERY = 10;
    private static final int MAX_LINK_LENGTH = 768;
    private static final int MAX_TITLE_LENGTH = 500;

    /** 카테고리별 수집 키워드 (검색어 → 카테고리) */
    private static final Map<String, NewsCategory> KEYWORDS = buildKeywords();

    /** 주요 언론사 도메인 → 표시 이름 */
    private static final Map<String, String> PRESS_NAMES = buildPressNames();

    private static Map<String, NewsCategory> buildKeywords() {
        Map<String, NewsCategory> keywords = new LinkedHashMap<>();
        keywords.put("노인학대", NewsCategory.ABUSE);
        keywords.put("요양시설 안전", NewsCategory.ABUSE);
        keywords.put("장기요양 수가", NewsCategory.POLICY);
        keywords.put("장기요양보험", NewsCategory.POLICY);
        keywords.put("요양보호사 처우", NewsCategory.POLICY);
        keywords.put("장기요양기관 평가", NewsCategory.EVAL);
        keywords.put("주간보호센터", NewsCategory.FIELD);
        keywords.put("요양보호사", NewsCategory.FIELD);
        return keywords;
    }

    private static Map<String, String> buildPressNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("yna.co.kr", "연합뉴스");
        names.put("newsis.com", "뉴시스");
        names.put("news1.kr", "뉴스1");
        names.put("kbs.co.kr", "KBS");
        names.put("imbc.com", "MBC");
        names.put("sbs.co.kr", "SBS");
        names.put("ytn.co.kr", "YTN");
        names.put("hani.co.kr", "한겨레");
        names.put("chosun.com", "조선일보");
        names.put("joongang.co.kr", "중앙일보");
        names.put("donga.com", "동아일보");
        names.put("khan.co.kr", "경향신문");
        names.put("mk.co.kr", "매일경제");
        names.put("hankyung.com", "한국경제");
        names.put("bokjitimes.com", "복지타임즈");
        names.put("bosa.co.kr", "의학신문");
        names.put("docdocdoc.co.kr", "청년의사");
        names.put("welfarenews.net", "웰페어뉴스");
        return names;
    }

    private final NewsArticleRepository newsArticleRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${naver-news.client-id:}")
    private String naverClientId;

    @Value("${naver-news.client-secret:}")
    private String naverClientSecret;

    @Transactional(readOnly = true)
    public Page<NewsArticleDTO> getNews(String categoryKey, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NewsArticle> articles;
        if (categoryKey == null || categoryKey.isBlank() || "all".equalsIgnoreCase(categoryKey)) {
            articles = newsArticleRepository.findAllByOrderByPublishedAtDesc(pageable);
        } else {
            articles = newsArticleRepository.findByCategoryOrderByPublishedAtDesc(
                    NewsCategory.fromKey(categoryKey), pageable);
        }
        return articles.map(NewsArticleDTO::from);
    }

    /** 서버 기동 직후 저장된 기사가 없으면 1회 수집 (첫 배포에서도 바로 데이터가 보이도록) */
    @EventListener(ApplicationReadyEvent.class)
    public void collectOnStartupIfEmpty() {
        try {
            if (isConfigured() && newsArticleRepository.count() == 0) {
                log.info("[News] 저장된 기사가 없어 초기 수집을 시작합니다");
                collectNews();
            }
        } catch (Exception e) {
            log.error("[News] 초기 수집 실패 (서비스 기동에는 영향 없음)", e);
        }
    }

    /** 하루 4회 수집 (KST 06/10/14/18시) */
    @Scheduled(cron = "0 0 6,10,14,18 * * *", zone = "Asia/Seoul")
    public void collectNewsScheduled() {
        try {
            collectNews();
        } catch (Exception e) {
            log.error("[News] 정기 수집 실패", e);
        }
    }

    public void collectNews() {
        if (!isConfigured()) {
            log.warn("[News] naver-news.client-id/secret 미설정 — 뉴스 수집을 건너뜁니다 (developers.naver.com에서 발급)");
            return;
        }
        int saved = 0;
        for (Map.Entry<String, NewsCategory> entry : KEYWORDS.entrySet()) {
            try {
                saved += collectByKeyword(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("[News] 키워드 '{}' 수집 실패: {}", entry.getKey(), e.getMessage());
            }
        }
        log.info("[News] 수집 완료: 신규 {}건", saved);
    }

    private boolean isConfigured() {
        return naverClientId != null && !naverClientId.isBlank()
                && naverClientSecret != null && !naverClientSecret.isBlank();
    }

    private int collectByKeyword(String keyword, NewsCategory category) throws Exception {
        String url = "https://openapi.naver.com/v1/search/news.json?query="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&display=" + MAX_ITEMS_PER_QUERY + "&sort=date";

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Naver-Client-Id", naverClientId);
        headers.add("X-Naver-Client-Secret", naverClientSecret);
        String body = restTemplate.exchange(URI.create(url), HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getBody();
        if (body == null) {
            return 0;
        }

        JsonNode items = objectMapper.readTree(body).path("items");
        int saved = 0;
        for (JsonNode item : items) {
            String title = cleanText(item.path("title").asText(null));
            String originalLink = item.path("originallink").asText(null);
            String link = originalLink != null && !originalLink.isBlank()
                    ? originalLink
                    : item.path("link").asText(null);
            String pubDate = item.path("pubDate").asText(null);

            if (title == null || title.isBlank() || link == null || link.length() > MAX_LINK_LENGTH) {
                continue;
            }
            if (title.length() > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH);
            }
            if (newsArticleRepository.existsByLink(link) || newsArticleRepository.existsByTitle(title)) {
                continue;
            }

            newsArticleRepository.save(NewsArticle.builder()
                    .title(title)
                    .source(pressNameOf(link))
                    .category(category)
                    .link(link)
                    .publishedAt(parsePubDate(pubDate))
                    .build());
            saved++;
        }
        return saved;
    }

    /** 네이버 API 응답의 <b> 강조 태그와 HTML 엔티티를 제거한다. */
    private String cleanText(String text) {
        if (text == null) {
            return null;
        }
        return text
                .replaceAll("</?b>", "")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim();
    }

    /** 기사 원문 링크 도메인으로 언론사 이름을 추정한다. */
    private String pressNameOf(String link) {
        try {
            String host = URI.create(link).getHost();
            if (host == null) {
                return null;
            }
            String bareHost = host.startsWith("www.") ? host.substring(4) : host;
            for (Map.Entry<String, String> entry : PRESS_NAMES.entrySet()) {
                if (bareHost.endsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return bareHost;
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parsePubDate(String pubDate) {
        if (pubDate == null) {
            return LocalDateTime.now();
        }
        try {
            return ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH))
                    .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                    .toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
