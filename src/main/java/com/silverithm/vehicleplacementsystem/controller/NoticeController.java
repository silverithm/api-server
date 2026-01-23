package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.CreateNoticeRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.NoticeCommentDTO;
import com.silverithm.vehicleplacementsystem.dto.NoticeCommentRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.NoticeDTO;
import com.silverithm.vehicleplacementsystem.dto.NoticeReaderDTO;
import com.silverithm.vehicleplacementsystem.dto.UpdateNoticeRequestDTO;
import com.silverithm.vehicleplacementsystem.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Validated
public class NoticeController {

    private final NoticeService noticeService;

    /**
     * 공지사항 목록 조회 (관리자)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotices(
            @RequestParam Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        try {
            log.info("[Notice API] 공지사항 목록 조회: companyId={}", companyId);

            List<NoticeDTO> notices = noticeService.getNotices(
                    companyId, status, priority, searchQuery, startDate, endDate);
            Map<String, Long> stats = noticeService.getStats(companyId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "notices", notices,
                            "stats", stats
                    ));

        } catch (Exception e) {
            log.error("[Notice API] 공지사항 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "공지사항 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 게시된 공지사항 목록 조회 (직원용)
     */
    @GetMapping("/published")
    public ResponseEntity<Map<String, Object>> getPublishedNotices(
            @RequestParam Long companyId) {

        try {
            log.info("[Notice API] 게시된 공지사항 조회: companyId={}", companyId);

            List<NoticeDTO> notices = noticeService.getPublishedNotices(companyId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("notices", notices));

        } catch (Exception e) {
            log.error("[Notice API] 게시된 공지사항 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "공지사항 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 공지사항 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getNotice(@PathVariable Long id) {

        try {
            log.info("[Notice API] 공지사항 상세 조회: id={}", id);

            NoticeDTO notice = noticeService.getNotice(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("notice", notice));

        } catch (Exception e) {
            log.error("[Notice API] 공지사항 상세 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "공지사항 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 공지사항 등록 (관리자)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createNotice(
            @RequestParam Long companyId,
            @Valid @RequestBody CreateNoticeRequestDTO request,
            Authentication authentication) {

        try {
            log.info("[Notice API] 공지사항 등록: companyId={}, title={}", companyId, request.getTitle());

            // 인증 정보에서 작성자 정보 추출
            String authorId = authentication != null ? authentication.getName() : "unknown";
            String authorName = "관리자"; // 필요시 인증 정보에서 이름 추출

            NoticeDTO notice = noticeService.createNotice(companyId, authorId, authorName, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "notice", notice,
                            "message", "공지사항이 등록되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Notice API] 공지사항 등록 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "공지사항 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 공지사항 수정 (관리자)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateNotice(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNoticeRequestDTO request) {

        try {
            log.info("[Notice API] 공지사항 수정: id={}", id);

            NoticeDTO notice = noticeService.updateNotice(id, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "notice", notice,
                            "message", "공지사항이 수정되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Notice API] 공지사항 수정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "공지사항 수정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 공지사항 삭제 (관리자)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteNotice(@PathVariable Long id) {

        try {
            log.info("[Notice API] 공지사항 삭제: id={}", id);

            noticeService.deleteNotice(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "공지사항이 삭제되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Notice API] 공지사항 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "공지사항 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 조회수 증가
     */
    @PostMapping("/{id}/view")
    public ResponseEntity<Map<String, Object>> incrementViewCount(@PathVariable Long id) {

        try {
            log.info("[Notice API] 조회수 증가: id={}", id);

            NoticeDTO notice = noticeService.incrementViewCount(id);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "viewCount", notice.getViewCount()
                    ));

        } catch (Exception e) {
            log.error("[Notice API] 조회수 증가 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "조회수 증가 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // ==================== 댓글 API ====================

    /**
     * 댓글 목록 조회
     */
    @GetMapping("/{noticeId}/comments")
    public ResponseEntity<Map<String, Object>> getComments(@PathVariable Long noticeId) {

        try {
            log.info("[Notice API] 댓글 목록 조회: noticeId={}", noticeId);

            List<NoticeCommentDTO> comments = noticeService.getComments(noticeId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("comments", comments));

        } catch (Exception e) {
            log.error("[Notice API] 댓글 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "댓글 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 댓글 생성
     */
    @PostMapping("/{noticeId}/comments")
    public ResponseEntity<Map<String, Object>> createComment(
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeCommentRequestDTO request,
            Authentication authentication) {

        try {
            log.info("[Notice API] 댓글 생성: noticeId={}", noticeId);

            // 인증 정보에서 작성자 정보 추출, 또는 요청에서 가져옴
            String authorId = request.getAuthorId() != null ? request.getAuthorId() :
                    (authentication != null ? authentication.getName() : "unknown");
            String authorName = request.getAuthorName() != null ? request.getAuthorName() : "사용자";

            NoticeCommentDTO comment = noticeService.createComment(noticeId, authorId, authorName, request.getContent());

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "comment", comment,
                            "message", "댓글이 등록되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Notice API] 댓글 생성 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "댓글 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 댓글 삭제
     */
    @DeleteMapping("/{noticeId}/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable Long noticeId,
            @PathVariable Long commentId) {

        try {
            log.info("[Notice API] 댓글 삭제: noticeId={}, commentId={}", noticeId, commentId);

            noticeService.deleteComment(noticeId, commentId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "댓글이 삭제되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Notice API] 댓글 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "댓글 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // ==================== 읽음 확인 API ====================

    /**
     * 읽은 사용자 목록 조회
     */
    @GetMapping("/{noticeId}/readers")
    public ResponseEntity<Map<String, Object>> getReaders(@PathVariable Long noticeId) {

        try {
            log.info("[Notice API] 읽은 사용자 목록 조회: noticeId={}", noticeId);

            List<NoticeReaderDTO> readers = noticeService.getReaders(noticeId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("readers", readers));

        } catch (Exception e) {
            log.error("[Notice API] 읽은 사용자 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "읽은 사용자 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .headers(getCorsHeaders())
                .build();
    }

    private HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        return headers;
    }
}