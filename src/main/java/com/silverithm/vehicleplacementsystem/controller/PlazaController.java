package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.PlazaDTO;
import com.silverithm.vehicleplacementsystem.service.PlazaService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 케어브이 광장 API.
 * GET(읽기·다운로드)은 permitAll(비로그인 공개), 쓰기는 인증 필요 (WebSecurityConfigure 참조).
 */
@RestController
@RequestMapping("/api/v1/plaza")
@RequiredArgsConstructor
@Slf4j
public class PlazaController {

    private final PlazaService plazaService;

    private String currentUserId(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    private String requireUserId(Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            throw new IllegalStateException("로그인이 필요합니다");
        }
        return userId;
    }

    // ── 내 광장 권한 ────────────────────────────────────────

    /**
     * 로그인 사용자의 광장 권한.
     * 비로그인이면 isAdmin=false로 응답한다 (프론트가 분기 없이 호출할 수 있게).
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyPlazaRole(Authentication authentication) {
        try {
            String userId = currentUserId(authentication);
            return ResponseEntity.ok(Map.of("isAdmin", plazaService.isPlazaAdmin(userId)));
        } catch (Exception e) {
            log.error("[Plaza API] 광장 권한 조회 오류:", e);
            return ResponseEntity.ok(Map.of("isAdmin", false));
        }
    }

    /**
     * [운영] 시스템 공지 목록 — 관리자 대시보드 공지 위젯에서 기관 공지와 함께 보여준다.
     * 비로그인도 조회 가능(GET permitAll).
     */
    @GetMapping("/notices")
    public ResponseEntity<?> getOfficialNotices(@RequestParam(defaultValue = "5") int size) {
        try {
            return ResponseEntity.ok(Map.of("notices", plazaService.getOfficialNotices(size)));
        } catch (Exception e) {
            log.error("[Plaza API] 운영 공지 조회 오류:", e);
            return ResponseEntity.ok(Map.of("notices", java.util.List.of()));
        }
    }

    // ── 게시글 ─────────────────────────────────────────────

    @GetMapping("/posts")
    public ResponseEntity<?> getPosts(
            @RequestParam(required = false) String board,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        try {
            int safeSize = Math.min(Math.max(size, 1), 50);
            return ResponseEntity.ok(plazaService.getPosts(board, category, sort, search, Math.max(page, 0), safeSize,
                    currentUserId(authentication)));
        } catch (Exception e) {
            log.error("[Plaza API] 게시글 목록 조회 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "게시글 목록 조회 중 오류가 발생했습니다"));
        }
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<?> getPost(@PathVariable Long postId, Authentication authentication) {
        try {
            return ResponseEntity.ok(plazaService.getPost(postId, currentUserId(authentication)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 게시글 조회 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "게시글 조회 중 오류가 발생했습니다"));
        }
    }

    /** isOfficial/isPinned는 광장 운영자만 반영된다 (서비스에서 검증) */
    public record PostRequest(String board, String category, String title, String content, Boolean isAnonymous,
                              String authorName, String companyName,
                              Boolean isOfficial, Boolean isPinned) {
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@RequestBody PostRequest request, Authentication authentication) {
        try {
            String userId = requireUserId(authentication);
            if (request.title() == null || request.title().isBlank()
                    || request.content() == null || request.content().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "제목과 내용을 입력해주세요"));
            }
            Long id = plazaService.createPost(
                    request.board() != null ? request.board() : "free",
                    request.category(),
                    request.title().trim(),
                    request.content().trim(),
                    Boolean.TRUE.equals(request.isAnonymous()),
                    userId,
                    request.authorName() != null ? request.authorName() : "사용자",
                    request.companyName(),
                    Boolean.TRUE.equals(request.isOfficial()),
                    Boolean.TRUE.equals(request.isPinned()));
            return ResponseEntity.ok(Map.of("id", id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 게시글 작성 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "게시글 작성 중 오류가 발생했습니다"));
        }
    }

    @PutMapping("/posts/{postId}")
    public ResponseEntity<?> updatePost(@PathVariable Long postId, @RequestBody PostRequest request,
                                        Authentication authentication) {
        try {
            plazaService.updatePost(postId,
                    request.board() != null ? request.board() : "free",
                    request.category(),
                    request.title().trim(),
                    request.content().trim(),
                    Boolean.TRUE.equals(request.isAnonymous()),
                    requireUserId(authentication));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 게시글 수정 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "게시글 수정 중 오류가 발생했습니다"));
        }
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable Long postId, Authentication authentication) {
        try {
            plazaService.deletePost(postId, requireUserId(authentication));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 게시글 삭제 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "게시글 삭제 중 오류가 발생했습니다"));
        }
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long postId, Authentication authentication) {
        try {
            boolean liked = plazaService.toggleLike(postId, requireUserId(authentication));
            return ResponseEntity.ok(Map.of("liked", liked));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 좋아요 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "좋아요 처리 중 오류가 발생했습니다"));
        }
    }

    public record ReportRequest(String reason) {
    }

    @PostMapping("/posts/{postId}/report")
    public ResponseEntity<?> reportPost(@PathVariable Long postId, @RequestBody ReportRequest request,
                                        Authentication authentication) {
        try {
            String result = plazaService.reportPost(postId,
                    request.reason() != null ? request.reason() : "기타",
                    requireUserId(authentication));
            return ResponseEntity.ok(Map.of("result", result));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 게시글 신고 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "신고 처리 중 오류가 발생했습니다"));
        }
    }

    // ── 댓글 ─────────────────────────────────────────────

    public record CommentRequest(Long parentId, String content, Boolean isAnonymous,
                                 String authorName, String companyName) {
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long postId, @RequestBody CommentRequest request,
                                        Authentication authentication) {
        try {
            String userId = requireUserId(authentication);
            if (request.content() == null || request.content().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "댓글 내용을 입력해주세요"));
            }
            plazaService.addComment(postId, request.parentId(), request.content().trim(),
                    Boolean.TRUE.equals(request.isAnonymous()),
                    userId,
                    request.authorName() != null ? request.authorName() : "사용자",
                    request.companyName());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 댓글 작성 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "댓글 작성 중 오류가 발생했습니다"));
        }
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<?> updateComment(@PathVariable Long commentId, @RequestBody CommentRequest request,
                                           Authentication authentication) {
        try {
            plazaService.updateComment(commentId, request.content().trim(), requireUserId(authentication));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 댓글 수정 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "댓글 수정 중 오류가 발생했습니다"));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, Authentication authentication) {
        try {
            plazaService.deleteComment(commentId, requireUserId(authentication));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 댓글 삭제 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "댓글 삭제 중 오류가 발생했습니다"));
        }
    }

    @PostMapping("/comments/{commentId}/accept")
    public ResponseEntity<?> acceptComment(@PathVariable Long commentId, Authentication authentication) {
        try {
            plazaService.acceptComment(commentId, requireUserId(authentication));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 답변 채택 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "답변 채택 중 오류가 발생했습니다"));
        }
    }

    // ── 자료실 ────────────────────────────────────────────

    /** 자료실 이용 자격 사전 확인 — 자유게시판 글 1개 이상 필요 */
    @GetMapping("/library/access")
    public ResponseEntity<?> getLibraryAccess(Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(Map.of("allowed", false, "reason", "LOGIN_REQUIRED"));
        }
        boolean allowed = plazaService.canAccessLibrary(userId);
        return ResponseEntity.ok(allowed
                ? Map.of("allowed", true)
                : Map.of("allowed", false, "reason", "FREE_POST_REQUIRED"));
    }

    @GetMapping("/library")
    public ResponseEntity<?> getLibraryItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        try {
            String userId = currentUserId(authentication);
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "자료실은 로그인 후 이용할 수 있습니다", "code", "LOGIN_REQUIRED"));
            }
            if (!plazaService.canAccessLibrary(userId)) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "자료실은 자유게시판에 글을 1개 이상 작성한 회원만 이용할 수 있습니다",
                                "code", "FREE_POST_REQUIRED"));
            }
            int safeSize = Math.min(Math.max(size, 1), 50);
            return ResponseEntity.ok(plazaService.getLibraryItems(category, search, Math.max(page, 0), safeSize,
                    userId));
        } catch (Exception e) {
            log.error("[Plaza API] 자료 목록 조회 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "자료 목록 조회 중 오류가 발생했습니다"));
        }
    }

    @PostMapping("/library")
    public ResponseEntity<?> uploadLibraryItem(
            @RequestParam("file") MultipartFile file,
            @RequestParam String category,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String uploaderName,
            @RequestParam(required = false) String companyName,
            Authentication authentication) {
        try {
            String userId = requireUserId(authentication);
            if (file.isEmpty() || title == null || title.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "제목과 파일이 필요합니다"));
            }
            Long id = plazaService.uploadLibraryItem(category, title.trim(),
                    description != null ? description.trim() : null,
                    file, userId,
                    uploaderName != null ? uploaderName : "사용자",
                    companyName);
            return ResponseEntity.ok(Map.of("id", id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 자료 업로드 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "자료 업로드 중 오류가 발생했습니다"));
        }
    }

    @GetMapping("/library/{itemId}/download")
    public ResponseEntity<?> downloadLibraryItem(@PathVariable Long itemId, Authentication authentication) {
        try {
            String userId = currentUserId(authentication);
            if (userId == null || userId.isBlank() || !plazaService.canAccessLibrary(userId)) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "자료실은 자유게시판에 글을 1개 이상 작성한 회원만 이용할 수 있습니다",
                                "code", "FREE_POST_REQUIRED"));
            }
            Object[] result = plazaService.downloadLibraryItem(itemId);
            String fileName = (String) result[0];
            byte[] bytes = (byte[]) result[1];

            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 자료 다운로드 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "자료 다운로드 중 오류가 발생했습니다"));
        }
    }

    public record LibraryUpdateRequest(String category, String title, String description) {
    }

    /** 자료 정보 수정 — 파일은 그대로 두고 제목·분류·설명만 바꾼다 */
    @PutMapping("/library/{itemId}")
    public ResponseEntity<?> updateLibraryItem(@PathVariable Long itemId,
                                               @RequestBody LibraryUpdateRequest request,
                                               Authentication authentication) {
        try {
            plazaService.updateLibraryItem(itemId, request.category(), request.title(), request.description(),
                    requireUserId(authentication));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 자료 수정 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "자료 수정 중 오류가 발생했습니다"));
        }
    }

    @DeleteMapping("/library/{itemId}")
    public ResponseEntity<?> deleteLibraryItem(@PathVariable Long itemId, Authentication authentication) {
        try {
            plazaService.deleteLibraryItem(itemId, requireUserId(authentication));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 자료 삭제 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "자료 삭제 중 오류가 발생했습니다"));
        }
    }

    @PostMapping("/library/{itemId}/report")
    public ResponseEntity<?> reportLibraryItem(@PathVariable Long itemId, @RequestBody ReportRequest request,
                                               Authentication authentication) {
        try {
            String result = plazaService.reportLibraryItem(itemId,
                    request.reason() != null ? request.reason() : "기타",
                    requireUserId(authentication));
            return ResponseEntity.ok(Map.of("result", result));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Plaza API] 자료 신고 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "신고 처리 중 오류가 발생했습니다"));
        }
    }
}
