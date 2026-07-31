package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.PlazaComment;
import com.silverithm.vehicleplacementsystem.entity.PlazaLibraryItem;
import com.silverithm.vehicleplacementsystem.entity.PlazaPost;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 케어브이 광장 API DTO 모음.
 * 익명 글/댓글은 서버에서 작성자 정보를 마스킹해 내려보낸다 (프론트 마스킹은 우회 가능).
 */
public final class PlazaDTO {

    private PlazaDTO() {
    }

    public record PostSummary(
            Long id,
            String board,
            String title,
            String preview,
            String displayAuthor,
            boolean isAnonymous,
            boolean isPinned,
            boolean isMine,
            int viewCount,
            long likeCount,
            long commentCount,
            boolean likedByMe,
            boolean hasAccepted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PostDetail(
            Long id,
            String board,
            String title,
            String content,
            String displayAuthor,
            boolean isAnonymous,
            boolean isPinned,
            boolean isMine,
            int viewCount,
            long likeCount,
            boolean likedByMe,
            boolean reportedByMe,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<CommentDTO> comments
    ) {
    }

    public record CommentDTO(
            Long id,
            Long parentId,
            String displayAuthor,
            boolean isAnonymous,
            boolean isAccepted,
            boolean isMine,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record LibraryItemDTO(
            Long id,
            String category,
            String title,
            String description,
            String fileName,
            long fileSize,
            String displayUploader,
            boolean isMine,
            boolean reportedByMe,
            int downloadCount,
            LocalDateTime createdAt
    ) {
    }

    public static String displayAuthor(boolean anonymous, String companyName, String authorName) {
        if (anonymous) {
            return "익명";
        }
        return (companyName != null && !companyName.isBlank() ? companyName + " · " : "") + authorName;
    }

    public static PostSummary toSummary(PlazaPost post, String currentUserId,
                                        long likeCount, long commentCount,
                                        boolean likedByMe, boolean hasAccepted) {
        String preview = post.getContent().replaceAll("\\s+", " ").trim();
        if (preview.length() > 150) {
            preview = preview.substring(0, 150);
        }
        return new PostSummary(
                post.getId(),
                post.getBoard().getKey(),
                post.getTitle(),
                preview,
                displayAuthor(post.isAnonymous(), post.getCompanyName(), post.getAuthorName()),
                post.isAnonymous(),
                post.isPinned(),
                currentUserId != null && currentUserId.equals(post.getAuthorId()),
                post.getViewCount(),
                likeCount,
                commentCount,
                likedByMe,
                hasAccepted,
                post.getCreatedAt(),
                post.getModifiedAt()
        );
    }

    public static CommentDTO toComment(PlazaComment comment, String currentUserId) {
        return new CommentDTO(
                comment.getId(),
                comment.getParentId(),
                displayAuthor(comment.isAnonymous(), comment.getCompanyName(), comment.getAuthorName()),
                comment.isAnonymous(),
                comment.isAccepted(),
                currentUserId != null && currentUserId.equals(comment.getAuthorId()),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getModifiedAt()
        );
    }

    public static LibraryItemDTO toLibraryItem(PlazaLibraryItem item, String currentUserId, boolean reportedByMe) {
        return new LibraryItemDTO(
                item.getId(),
                item.getCategory().getKey(),
                item.getTitle(),
                item.getDescription(),
                item.getFileName(),
                item.getFileSize(),
                displayAuthor(false, item.getCompanyName(), item.getUploaderName()),
                currentUserId != null && currentUserId.equals(item.getUploaderId()),
                reportedByMe,
                item.getDownloadCount(),
                item.getCreatedAt()
        );
    }
}
