package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.NewsArticleDTO;
import com.silverithm.vehicleplacementsystem.service.NewsService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 케어브이 광장 - 요양 소식 API
 */
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNews(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            int safeSize = Math.min(Math.max(size, 1), 50);
            Page<NewsArticleDTO> news = newsService.getNews(category, Math.max(page, 0), safeSize);

            return ResponseEntity.ok(Map.of(
                    "content", news.getContent(),
                    "totalPages", news.getTotalPages(),
                    "totalElements", news.getTotalElements()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[News API] 뉴스 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "뉴스 조회 중 오류가 발생했습니다"));
        }
    }
}
