package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PlazaDTO;
import com.silverithm.vehicleplacementsystem.entity.PlazaComment;
import com.silverithm.vehicleplacementsystem.entity.PlazaLibraryItem;
import com.silverithm.vehicleplacementsystem.entity.PlazaPost;
import com.silverithm.vehicleplacementsystem.entity.PlazaPostLike;
import com.silverithm.vehicleplacementsystem.entity.PlazaPostView;
import com.silverithm.vehicleplacementsystem.entity.PlazaReport;
import com.silverithm.vehicleplacementsystem.repository.PlazaCommentRepository;
import com.silverithm.vehicleplacementsystem.repository.PlazaLibraryItemRepository;
import com.silverithm.vehicleplacementsystem.repository.PlazaPostLikeRepository;
import com.silverithm.vehicleplacementsystem.repository.PlazaPostRepository;
import com.silverithm.vehicleplacementsystem.repository.PlazaPostViewRepository;
import com.silverithm.vehicleplacementsystem.repository.PlazaReportRepository;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 케어브이 광장 (게시판·자료실) — 전 기관 공유 리소스.
 * 읽기는 비로그인 허용(currentUserId=null), 쓰기는 인증 사용자만 (컨트롤러/시큐리티에서 보장).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlazaService {

    private static final int REPORT_AUTO_HIDE_THRESHOLD = 3;

    private final PlazaPostRepository postRepository;
    private final PlazaCommentRepository commentRepository;
    private final PlazaPostLikeRepository likeRepository;
    private final PlazaPostViewRepository viewRepository;
    private final PlazaReportRepository reportRepository;
    private final PlazaLibraryItemRepository libraryRepository;
    private final FileStorageService fileStorageService;

    // ── 게시글 ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getPosts(String boardKey, String sort, String search,
                                        int page, int size, String currentUserId) {
        PlazaPost.Board board = boardKey == null || boardKey.isBlank() || "all".equalsIgnoreCase(boardKey)
                ? null : PlazaPost.Board.fromKey(boardKey);
        String query = search == null || search.isBlank() ? null : search.trim();

        // 정렬: 고정글 우선 + (최신 | 조회수). 좋아요/댓글순은 집계 후 재정렬이 필요해 최신순으로 대체하지 않고
        // viewCount는 컬럼이라 DB 정렬 가능. likes/comments는 아래에서 페이지 내 재정렬.
        Sort dbSort = Sort.by(Sort.Direction.DESC, "isPinned").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PlazaPost> posts = postRepository.findVisible(board, query, PageRequest.of(page, size, dbSort));

        List<Long> ids = posts.getContent().stream().map(PlazaPost::getId).toList();
        Map<Long, Long> likeCounts = toCountMap(ids.isEmpty() ? List.of() : likeRepository.countByPostIds(ids));
        Map<Long, Long> commentCounts = toCountMap(ids.isEmpty() ? List.of() : commentRepository.countByPostIds(ids));
        Set<Long> likedByMe = currentUserId == null || ids.isEmpty()
                ? Set.of() : new HashSet<>(likeRepository.findLikedPostIds(currentUserId, ids));
        Set<Long> accepted = new HashSet<>();
        for (Long id : ids) {
            // Q&A 채택 표시용 — 페이지 내 소량이라 개별 조회 허용
            boolean hasAccepted = commentRepository.findByPostIdOrderByCreatedAtAsc(id).stream()
                    .anyMatch(PlazaComment::isAccepted);
            if (hasAccepted) {
                accepted.add(id);
            }
        }

        List<PlazaDTO.PostSummary> content = posts.getContent().stream()
                .map(p -> PlazaDTO.toSummary(p, currentUserId,
                        likeCounts.getOrDefault(p.getId(), 0L),
                        commentCounts.getOrDefault(p.getId(), 0L),
                        likedByMe.contains(p.getId()),
                        accepted.contains(p.getId())))
                .sorted((a, b) -> {
                    if (a.isPinned() != b.isPinned()) {
                        return a.isPinned() ? -1 : 1;
                    }
                    return switch (sort == null ? "latest" : sort) {
                        case "popular" -> Long.compare(b.likeCount(), a.likeCount());
                        case "comments" -> Long.compare(b.commentCount(), a.commentCount());
                        default -> b.createdAt().compareTo(a.createdAt());
                    };
                })
                .toList();

        return Map.of(
                "content", content,
                "totalPages", posts.getTotalPages(),
                "totalElements", posts.getTotalElements()
        );
    }

    @Transactional
    public PlazaDTO.PostDetail getPost(Long postId, String currentUserId) {
        PlazaPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다"));
        if (post.isHidden()) {
            throw new IllegalArgumentException("숨김 처리된 게시글입니다");
        }

        // 사용자당 1회 조회수 (비로그인은 카운트 제외)
        if (currentUserId != null && !viewRepository.existsByPostIdAndUserId(postId, currentUserId)) {
            try {
                viewRepository.save(PlazaPostView.builder().postId(postId).userId(currentUserId).build());
                post.setViewCount(post.getViewCount() + 1);
            } catch (DataIntegrityViolationException ignored) {
                // 동시 요청 중복 — 무시
            }
        }

        List<PlazaDTO.CommentDTO> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(c -> PlazaDTO.toComment(c, currentUserId))
                .toList();

        return new PlazaDTO.PostDetail(
                post.getId(),
                post.getBoard().getKey(),
                post.getTitle(),
                post.getContent(),
                PlazaDTO.displayAuthor(post.isAnonymous(), post.getCompanyName(), post.getAuthorName()),
                post.isAnonymous(),
                post.isPinned(),
                currentUserId != null && currentUserId.equals(post.getAuthorId()),
                post.getViewCount(),
                likeRepository.countByPostId(postId),
                currentUserId != null && likeRepository.findByPostIdAndUserId(postId, currentUserId).isPresent(),
                currentUserId != null && reportRepository.existsByTargetTypeAndTargetIdAndReporterId(
                        PlazaReport.TargetType.POST, postId, currentUserId),
                post.getCreatedAt(),
                post.getModifiedAt(),
                comments
        );
    }

    @Transactional
    public Long createPost(String board, String title, String content, boolean anonymous,
                           String authorId, String authorName, String companyName) {
        PlazaPost post = PlazaPost.builder()
                .board(PlazaPost.Board.fromKey(board))
                .title(title)
                .content(content)
                .authorId(authorId)
                .authorName(authorName)
                .companyName(companyName)
                .isAnonymous(anonymous)
                .isPinned(false)
                .isHidden(false)
                .viewCount(0)
                .build();
        return postRepository.save(post).getId();
    }

    @Transactional
    public void updatePost(Long postId, String board, String title, String content, boolean anonymous, String userId) {
        PlazaPost post = requireOwnPost(postId, userId);
        post.setBoard(PlazaPost.Board.fromKey(board));
        post.setTitle(title);
        post.setContent(content);
        post.setAnonymous(anonymous);
    }

    @Transactional
    public void deletePost(Long postId, String userId) {
        PlazaPost post = requireOwnPost(postId, userId);
        postRepository.delete(post); // 댓글/좋아요/조회는 FK ON DELETE CASCADE
    }

    @Transactional
    public boolean toggleLike(Long postId, String userId) {
        var existing = likeRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            return false;
        }
        likeRepository.save(PlazaPostLike.builder().postId(postId).userId(userId).build());
        return true;
    }

    /** @return reported | already | hidden */
    @Transactional
    public String reportPost(Long postId, String reason, String userId) {
        if (reportRepository.existsByTargetTypeAndTargetIdAndReporterId(PlazaReport.TargetType.POST, postId, userId)) {
            return "already";
        }
        reportRepository.save(PlazaReport.builder()
                .targetType(PlazaReport.TargetType.POST).targetId(postId).reporterId(userId).reason(reason).build());
        long count = reportRepository.countByTargetTypeAndTargetId(PlazaReport.TargetType.POST, postId);
        if (count >= REPORT_AUTO_HIDE_THRESHOLD) {
            postRepository.findById(postId).ifPresent(p -> p.setHidden(true));
            return "hidden";
        }
        return "reported";
    }

    // ── 댓글 ─────────────────────────────────────────────

    @Transactional
    public void addComment(Long postId, Long parentId, String content, boolean anonymous,
                           String authorId, String authorName, String companyName) {
        postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다"));
        commentRepository.save(PlazaComment.builder()
                .postId(postId)
                .parentId(parentId)
                .authorId(authorId)
                .authorName(authorName)
                .companyName(companyName)
                .isAnonymous(anonymous)
                .isAccepted(false)
                .content(content)
                .build());
    }

    @Transactional
    public void updateComment(Long commentId, String content, String userId) {
        PlazaComment comment = requireOwnComment(commentId, userId);
        comment.setContent(content);
    }

    @Transactional
    public void deleteComment(Long commentId, String userId) {
        requireOwnComment(commentId, userId);
        commentRepository.deleteWithReplies(commentId);
    }

    /** Q&A 답변 채택 — 글 작성자만, 글당 1개 */
    @Transactional
    public void acceptComment(Long commentId, String userId) {
        PlazaComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다"));
        PlazaPost post = postRepository.findById(comment.getPostId())
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다"));
        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalStateException("글 작성자만 답변을 채택할 수 있습니다");
        }
        commentRepository.acceptOnly(post.getId(), commentId);
    }

    // ── 자료실 ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getLibraryItems(String categoryKey, String search, int page, int size, String currentUserId) {
        PlazaLibraryItem.Category category = categoryKey == null || categoryKey.isBlank() || "all".equalsIgnoreCase(categoryKey)
                ? null : PlazaLibraryItem.Category.fromKey(categoryKey);
        String query = search == null || search.isBlank() ? null : search.trim();

        Page<PlazaLibraryItem> items = libraryRepository.findVisible(category, query,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<PlazaDTO.LibraryItemDTO> content = items.getContent().stream()
                .map(i -> PlazaDTO.toLibraryItem(i, currentUserId,
                        currentUserId != null && reportRepository.existsByTargetTypeAndTargetIdAndReporterId(
                                PlazaReport.TargetType.LIBRARY, i.getId(), currentUserId)))
                .toList();

        return Map.of(
                "content", content,
                "totalPages", items.getTotalPages(),
                "totalElements", items.getTotalElements()
        );
    }

    @Transactional
    public Long uploadLibraryItem(String categoryKey, String title, String description, MultipartFile file,
                                  String uploaderId, String uploaderName, String companyName) throws IOException {
        String storedPath = fileStorageService.storeFile(file, "plaza");
        PlazaLibraryItem item = PlazaLibraryItem.builder()
                .category(PlazaLibraryItem.Category.fromKey(categoryKey))
                .title(title)
                .description(description)
                .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file")
                .fileSize(file.getSize())
                .filePath(storedPath)
                .uploaderId(uploaderId)
                .uploaderName(uploaderName)
                .companyName(companyName)
                .downloadCount(0)
                .isHidden(false)
                .build();
        return libraryRepository.save(item).getId();
    }

    /** 다운로드: 카운트 증가 후 파일 바이트 반환. [fileName, bytes] */
    @Transactional
    public Object[] downloadLibraryItem(Long itemId) throws IOException {
        PlazaLibraryItem item = libraryRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("자료를 찾을 수 없습니다"));
        if (item.isHidden()) {
            throw new IllegalArgumentException("숨김 처리된 자료입니다");
        }
        byte[] bytes = fileStorageService.loadFile(item.getFilePath());
        item.setDownloadCount(item.getDownloadCount() + 1);
        return new Object[]{item.getFileName(), bytes};
    }

    @Transactional
    public void deleteLibraryItem(Long itemId, String userId) {
        PlazaLibraryItem item = libraryRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("자료를 찾을 수 없습니다"));
        if (!item.getUploaderId().equals(userId)) {
            throw new IllegalStateException("본인이 올린 자료만 삭제할 수 있습니다");
        }
        try {
            fileStorageService.deleteFile(item.getFilePath());
        } catch (IOException e) {
            log.warn("[Plaza] 자료 파일 삭제 실패 (메타는 삭제 진행): {}", item.getFilePath(), e);
        }
        libraryRepository.delete(item);
    }

    /** @return reported | already */
    @Transactional
    public String reportLibraryItem(Long itemId, String reason, String userId) {
        if (reportRepository.existsByTargetTypeAndTargetIdAndReporterId(PlazaReport.TargetType.LIBRARY, itemId, userId)) {
            return "already";
        }
        reportRepository.save(PlazaReport.builder()
                .targetType(PlazaReport.TargetType.LIBRARY).targetId(itemId).reporterId(userId).reason(reason).build());
        long count = reportRepository.countByTargetTypeAndTargetId(PlazaReport.TargetType.LIBRARY, itemId);
        if (count >= REPORT_AUTO_HIDE_THRESHOLD) {
            libraryRepository.findById(itemId).ifPresent(i -> i.setHidden(true));
        }
        return "reported";
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────

    private PlazaPost requireOwnPost(Long postId, String userId) {
        PlazaPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다"));
        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalStateException("본인이 작성한 글만 수정/삭제할 수 있습니다");
        }
        return post;
    }

    private PlazaComment requireOwnComment(Long commentId, String userId) {
        PlazaComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다"));
        if (!comment.getAuthorId().equals(userId)) {
            throw new IllegalStateException("본인이 작성한 댓글만 수정/삭제할 수 있습니다");
        }
        return comment;
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
