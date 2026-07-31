package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.NewsArticle;
import com.silverithm.vehicleplacementsystem.entity.NewsCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    Page<NewsArticle> findAllByOrderByPublishedAtDesc(Pageable pageable);

    Page<NewsArticle> findByCategoryOrderByPublishedAtDesc(NewsCategory category, Pageable pageable);

    boolean existsByLink(String link);

    boolean existsByTitle(String title);
}
