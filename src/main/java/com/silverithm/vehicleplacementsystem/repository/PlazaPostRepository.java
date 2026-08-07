package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.PlazaPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlazaPostRepository extends JpaRepository<PlazaPost, Long> {

    @Query("""
            SELECT p FROM PlazaPost p
            WHERE p.isHidden = false
              AND (:board IS NULL OR p.board = :board)
              AND (:category IS NULL OR p.category = :category)
              AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.content) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<PlazaPost> findVisible(@Param("board") PlazaPost.Board board,
                                @Param("category") PlazaPost.Category category,
                                @Param("search") String search,
                                Pageable pageable);

    /** 자료실 이용 자격 확인용 — 이 사용자가 해당 게시판에 쓴 노출 글이 있는지 */
    boolean existsByAuthorIdAndBoardAndIsHiddenFalse(String authorId, PlazaPost.Board board);

    /** [운영] 시스템 공지만 최신순으로 — 관리자 대시보드 공지 위젯에서 쓴다. */
    @Query("""
            SELECT p FROM PlazaPost p
            WHERE p.isHidden = false AND p.isOfficial = true
            ORDER BY p.createdAt DESC
            """)
    Page<PlazaPost> findOfficial(Pageable pageable);
}
