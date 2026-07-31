package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.PlazaComment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlazaCommentRepository extends JpaRepository<PlazaComment, Long> {

    List<PlazaComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    long countByPostId(Long postId);

    /** 목록용 게시글별 댓글 수 일괄 조회: [postId, count] */
    @Query("SELECT c.postId, COUNT(c) FROM PlazaComment c WHERE c.postId IN :postIds GROUP BY c.postId")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    @Modifying
    @Query("DELETE FROM PlazaComment c WHERE c.id = :commentId OR c.parentId = :commentId")
    void deleteWithReplies(@Param("commentId") Long commentId);

    @Modifying
    @Query("UPDATE PlazaComment c SET c.isAccepted = (c.id = :commentId) WHERE c.postId = :postId")
    void acceptOnly(@Param("postId") Long postId, @Param("commentId") Long commentId);
}
