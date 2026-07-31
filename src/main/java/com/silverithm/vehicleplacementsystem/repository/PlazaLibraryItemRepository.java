package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.PlazaLibraryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlazaLibraryItemRepository extends JpaRepository<PlazaLibraryItem, Long> {

    @Query("""
            SELECT i FROM PlazaLibraryItem i
            WHERE i.isHidden = false
              AND (:category IS NULL OR i.category = :category)
              AND (:search IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(i.fileName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<PlazaLibraryItem> findVisible(@Param("category") PlazaLibraryItem.Category category,
                                       @Param("search") String search,
                                       Pageable pageable);
}
