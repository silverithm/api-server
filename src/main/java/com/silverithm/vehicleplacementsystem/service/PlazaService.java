package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PlazaDTO;
import com.silverithm.vehicleplacementsystem.entity.PlazaComment;
import com.silverithm.vehicleplacementsystem.entity.PlazaLibraryItem;
import com.silverithm.vehicleplacementsystem.entity.PlazaPost;
import com.silverithm.vehicleplacementsystem.entity.PlazaPostLike;
import com.silverithm.vehicleplacementsystem.entity.PlazaPostView;
import com.silverithm.vehicleplacementsystem.entity.PlazaReport;
import com.silverithm.vehicleplacementsystem.repository.PlazaAdminRepository;
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
    private final PlazaAdminRepository plazaAdminRepository;
    private final FileStorageService fileStorageService;

    // ── 게시글 ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getPosts(String boardKey, String categoryKey, String sort, String search,
                                        int page, int size, String currentUserId) {
        PlazaPost.Board board = boardKey == null || boardKey.isBlank() || "all".equalsIgnoreCase(boardKey)
                ? null : PlazaPost.Board.fromKey(boardKey);
        PlazaPost.Category category = categoryKey == null || categoryKey.isBlank() || "all".equalsIgnoreCase(categoryKey)
                ? null : PlazaPost.Category.fromKey(categoryKey);
        String query = search == null || search.isBlank() ? null : search.trim();

        // 정렬: 고정글 우선 + (최신 | 조회수). 좋아요/댓글순은 집계 후 재정렬이 필요해 최신순으로 대체하지 않고
        // viewCount는 컬럼이라 DB 정렬 가능. likes/comments는 아래에서 페이지 내 재정렬.
        Sort dbSort = Sort.by(Sort.Direction.DESC, "isPinned").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PlazaPost> posts = postRepository.findVisible(board, category, query, PageRequest.of(page, size, dbSort));

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
                post.getCategory() != null ? post.getCategory().getKey() : null,
                post.getTitle(),
                post.getContent(),
                PlazaDTO.postAuthor(post),
                post.isAnonymous(),
                post.isPinned(),
                post.isOfficial(),
                currentUserId != null && currentUserId.equals(post.getAuthorId()),
                post.getViewCount(),
                likeRepository.countByPostId(postId),
                currentUserId != null && likeRepository.findByPostIdAndUserId(postId, currentUserId).isPresent(),
                currentUserId != null && reportRepository.existsByTargetTypeAndTargetIdAndReporterId(
                        PlazaReport.TargetType.POST, postId, currentUserId),
                // 비공개 연락처는 로그인 회원에게만 (비로그인·크롤러에는 감춘다)
                post.isContactPublic() || currentUserId != null ? post.getContactInfo() : null,
                post.isContactPublic(),
                post.getCreatedAt(),
                post.getModifiedAt(),
                comments
        );
    }

    @Transactional
    public Long createPost(String board, String category, String title, String content, boolean anonymous,
                           String authorId, String authorName, String companyName,
                           boolean official, boolean pinned, String contactInfo, boolean contactPublic) {
        // [운영] 공지와 상단 고정은 광장 운영자만 쓸 수 있다. 일반 사용자가 보내면 무시한다.
        boolean admin = isPlazaAdmin(authorId);
        boolean asOfficial = admin && official;

        PlazaPost.Board boardValue = PlazaPost.Board.fromKey(board);
        PlazaPost post = PlazaPost.builder()
                .board(boardValue)
                // 시설 유형은 자유게시판에만 없다. 구인구직은 선택 사항이라 비어 있으면 null.
                .category(resolveCategory(boardValue, category))
                .contactInfo(normalizeContact(boardValue, contactInfo))
                .contactPublic(isJobBoard(boardValue) && contactPublic)
                .title(title)
                .content(content)
                .authorId(authorId)
                .authorName(authorName)
                .companyName(companyName)
                // 운영 공지는 작성자를 '케어브이 운영팀'으로 표시하므로 익명 처리와 함께 쓰지 않는다
                .isAnonymous(!asOfficial && anonymous)
                .isPinned(admin && pinned)
                .isOfficial(asOfficial)
                .isHidden(false)
                .viewCount(0)
                .build();
        return postRepository.save(post).getId();
    }

    @Transactional
    public void updatePost(Long postId, String board, String category, String title, String content,
                           boolean anonymous, String userId, String contactInfo, boolean contactPublic) {
        PlazaPost post = requireManageablePost(postId, userId);
        PlazaPost.Board boardValue = PlazaPost.Board.fromKey(board);
        post.setBoard(boardValue);
        post.setCategory(resolveCategory(boardValue, category));
        post.setContactInfo(normalizeContact(boardValue, contactInfo));
        post.setContactPublic(isJobBoard(boardValue) && contactPublic);
        post.setTitle(title);
        post.setContent(content);
        post.setAnonymous(!post.isOfficial() && anonymous);
    }

    @Transactional
    public void deletePost(Long postId, String userId) {
        PlazaPost post = requireManageablePost(postId, userId);
        postRepository.delete(post); // 댓글/좋아요/조회는 FK ON DELETE CASCADE
    }

    /**
     * [운영] 시스템 공지 목록 (제목·작성일 중심의 가벼운 응답).
     * 기관 공지와 함께 대시보드에 띄우기 위한 용도라 좋아요/댓글 집계는 하지 않는다.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOfficialNotices(int size) {
        return postRepository.findOfficial(PageRequest.of(0, Math.min(Math.max(size, 1), 20)))
                .getContent().stream()
                .map(post -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", post.getId());
                    item.put("board", post.getBoard().getKey());
                    item.put("title", post.getTitle());
                    item.put("displayAuthor", PlazaDTO.postAuthor(post));
                    item.put("isPinned", post.isPinned());
                    item.put("createdAt", post.getCreatedAt());
                    return item;
                })
                .toList();
    }

    /** 광장 운영자 여부 — 기관 관리자와는 별개 권한이다. */
    @Transactional(readOnly = true)
    public boolean isPlazaAdmin(String userId) {
        return userId != null && plazaAdminRepository.existsByEmail(userId);
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

    /**
     * 자료실 이용 자격: 자유게시판에 글을 1개 이상 쓴 회원만.
     * 받기만 하고 나누지 않는 이용을 막기 위한 참여 조건이다. 운영자는 항상 허용.
     */
    @Transactional(readOnly = true)
    public boolean canAccessLibrary(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (isPlazaAdmin(userId)) {
            return true;
        }
        return postRepository.existsByAuthorIdAndBoardAndIsHiddenFalse(userId, PlazaPost.Board.FREE);
    }

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
        // HEIC/HEIF 사진이면 JPEG 사본이 만들어지고 자료는 그 사본을 가리킨다(웹에서 열려야 한다).
        FileStorageService.StoredUpload stored = fileStorageService.storeUpload(file, "plaza");
        String storedPath = stored.path();
        PlazaLibraryItem item = PlazaLibraryItem.builder()
                .category(PlazaLibraryItem.Category.fromKey(categoryKey))
                .title(title)
                .description(description)
                .fileName(stored.fileName() != null ? stored.fileName() : "file")
                .fileSize(stored.size())
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

    /**
     * 자료 정보 수정 — 제목·분류·설명만 바꾼다.
     * 파일 자체를 바꾸려면 삭제 후 다시 올린다(다운로드 수 등 이력이 초기화되는 게 자연스럽다).
     * 커뮤니티 운영자는 관리 목적으로 다른 사람 자료도 수정할 수 있다.
     */
    @Transactional
    public void updateLibraryItem(Long itemId, String category, String title, String description, String userId) {
        PlazaLibraryItem item = libraryRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("자료를 찾을 수 없습니다"));
        if (!item.getUploaderId().equals(userId) && !isPlazaAdmin(userId)) {
            throw new IllegalStateException("본인이 올린 자료만 수정할 수 있습니다");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목을 입력해주세요");
        }
        item.setCategory(PlazaLibraryItem.Category.fromKey(category));
        item.setTitle(title.trim());
        item.setDescription(description == null || description.isBlank() ? null : description.trim());
    }

    @Transactional
    public void deleteLibraryItem(Long itemId, String userId) {
        PlazaLibraryItem item = libraryRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("자료를 찾을 수 없습니다"));
        if (!item.getUploaderId().equals(userId) && !isPlazaAdmin(userId)) {
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

    /** 본인 글이거나, 광장 운영자면 관리 목적으로 다른 사람 글도 다룰 수 있다. */
    private PlazaPost requireManageablePost(Long postId, String userId) {
        PlazaPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다"));
        if (!post.getAuthorId().equals(userId) && !isPlazaAdmin(userId)) {
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

    private static boolean isJobBoard(PlazaPost.Board board) {
        return board == PlazaPost.Board.JOB_OFFER || board == PlazaPost.Board.JOB_SEEK;
    }

    /** 시설 유형 결정 — 자유게시판은 항상 null, 구인구직은 선택 사항 */
    private static PlazaPost.Category resolveCategory(PlazaPost.Board board, String category) {
        if (board == PlazaPost.Board.FREE) {
            return null;
        }
        if (isJobBoard(board) && (category == null || category.isBlank())) {
            return null;
        }
        return PlazaPost.Category.fromKey(category);
    }

    /** 연락처는 구인구직 글에만 저장한다 */
    private static String normalizeContact(PlazaPost.Board board, String contactInfo) {
        if (!isJobBoard(board) || contactInfo == null || contactInfo.isBlank()) {
            return null;
        }
        return contactInfo.trim();
    }
}
