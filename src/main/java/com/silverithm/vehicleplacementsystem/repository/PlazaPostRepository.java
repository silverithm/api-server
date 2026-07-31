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
              AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.content) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<PlazaPost> findVisible(@Param("board") PlazaPost.Board board,
                                @Param("search") String search,
                                Pageable pageable);
}
