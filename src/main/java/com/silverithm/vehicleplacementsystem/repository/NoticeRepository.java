package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 회사별 전체 공지사항 조회 (최신순)
    List<Notice> findByCompanyIdOrderByIsPinnedDescCreatedAtDesc(Long companyId);

    // 회사별 게시된 공지사항 조회 (상단 고정 우선, 최신순)
    List<Notice> findByCompanyIdAndStatusOrderByIsPinnedDescPublishedAtDesc(
            Long companyId, Notice.NoticeStatus status);

    // 회사별 상태별 공지사항 조회
    List<Notice> findByCompanyIdAndStatusOrderByCreatedAtDesc(
            Long companyId, Notice.NoticeStatus status);

    // 회사별 우선순위별 공지사항 조회
    List<Notice> findByCompanyIdAndPriorityOrderByCreatedAtDesc(
            Long companyId, Notice.NoticePriority priority);

    // 검색 쿼리
    @Query("SELECT n FROM Notice n WHERE n.company.id = :companyId " +
           "AND (n.title LIKE %:query% OR n.content LIKE %:query% OR n.authorName LIKE %:query%) " +
           "ORDER BY n.isPinned DESC, n.createdAt DESC")
    List<Notice> searchByQuery(@Param("companyId") Long companyId, @Param("query") String query);

    // 기간별 공지사항 조회
    @Query("SELECT n FROM Notice n WHERE n.company.id = :companyId " +
           "AND n.createdAt >= :startDate AND n.createdAt <= :endDate " +
           "ORDER BY n.isPinned DESC, n.createdAt DESC")
    List<Notice> findByCompanyIdAndDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // 복합 필터링 쿼리
    @Query("SELECT n FROM Notice n WHERE n.company.id = :companyId " +
           "AND (:status IS NULL OR n.status = :status) " +
           "AND (:priority IS NULL OR n.priority = :priority) " +
           "AND (:query IS NULL OR n.title LIKE %:query% OR n.content LIKE %:query%) " +
           "AND (:startDate IS NULL OR n.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR n.createdAt <= :endDate) " +
           "ORDER BY n.isPinned DESC, n.createdAt DESC")
    List<Notice> findByFilters(
            @Param("companyId") Long companyId,
            @Param("status") Notice.NoticeStatus status,
            @Param("priority") Notice.NoticePriority priority,
            @Param("query") String query,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // 통계
    long countByCompanyIdAndStatus(Long companyId, Notice.NoticeStatus status);
    long countByCompanyId(Long companyId);
}