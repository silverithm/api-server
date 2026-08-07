package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // 회사별 전체 일정 조회 (시작일 기준 오름차순)
    List<Schedule> findByCompanyIdOrderByStartDateAscStartTimeAsc(Long companyId);

    /**
     * 오늘 진행 중인데 담당자가 아직 수행완료로 바꾸지 않은 일정 (미수행 알림용).
     * 여러 날에 걸친 일정도 오늘이 기간 안이면 대상이 된다.
     */
    @Query("SELECT s FROM Schedule s WHERE s.isCompleted = false "
            + "AND s.managerMemberId IS NOT NULL "
            + "AND s.startDate <= :today AND s.endDate >= :today")
    List<Schedule> findTodayIncompleteWithManager(@Param("today") LocalDate today);

    // 회사별 기간 내 일정 조회 (시작일 또는 종료일이 기간에 포함되는 일정)
    // 라벨은 DTO 변환에서 반드시 읽으므로 같이 가져온다 — 지연 로딩이면 일정 수만큼 쿼리가 더 나간다.
    @Query("SELECT s FROM Schedule s LEFT JOIN FETCH s.label WHERE s.company.id = :companyId " +
           "AND ((s.startDate >= :startDate AND s.startDate <= :endDate) " +
           "OR (s.endDate >= :startDate AND s.endDate <= :endDate) " +
           "OR (s.startDate <= :startDate AND s.endDate >= :endDate)) " +
           "ORDER BY s.startDate ASC, s.startTime ASC")
    List<Schedule> findByCompanyIdAndDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // 회사별 카테고리 필터 일정 조회
    @Query("SELECT s FROM Schedule s WHERE s.company.id = :companyId " +
           "AND (:category IS NULL OR s.category = :category) " +
           "AND ((s.startDate >= :startDate AND s.startDate <= :endDate) " +
           "OR (s.endDate >= :startDate AND s.endDate <= :endDate) " +
           "OR (s.startDate <= :startDate AND s.endDate >= :endDate)) " +
           "ORDER BY s.startDate ASC, s.startTime ASC")
    List<Schedule> findByCompanyIdAndCategoryAndDateRange(
            @Param("companyId") Long companyId,
            @Param("category") ScheduleCategory category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // 회사별 라벨 필터 일정 조회
    @Query("SELECT s FROM Schedule s WHERE s.company.id = :companyId " +
           "AND s.label.id = :labelId " +
           "ORDER BY s.startDate ASC, s.startTime ASC")
    List<Schedule> findByCompanyIdAndLabelId(
            @Param("companyId") Long companyId,
            @Param("labelId") Long labelId);

    // 검색 쿼리
    @Query("SELECT s FROM Schedule s WHERE s.company.id = :companyId " +
           "AND (s.title LIKE %:query% OR s.content LIKE %:query% OR s.location LIKE %:query%) " +
           "ORDER BY s.startDate ASC, s.startTime ASC")
    List<Schedule> searchByQuery(
            @Param("companyId") Long companyId,
            @Param("query") String query);

    // 복합 필터링 쿼리 (라벨은 DTO 변환에서 쓰므로 함께 가져온다)
    @Query("SELECT s FROM Schedule s LEFT JOIN FETCH s.label WHERE s.company.id = :companyId " +
           "AND (:category IS NULL OR s.category = :category) " +
           "AND (:labelId IS NULL OR s.label.id = :labelId) " +
           "AND (:query IS NULL OR s.title LIKE %:query% OR s.content LIKE %:query%) " +
           "AND (:startDate IS NULL OR (s.startDate >= :startDate OR s.endDate >= :startDate)) " +
           "AND (:endDate IS NULL OR (s.startDate <= :endDate OR s.endDate <= :endDate)) " +
           "ORDER BY s.startDate ASC, s.startTime ASC")
    List<Schedule> findByFilters(
            @Param("companyId") Long companyId,
            @Param("category") ScheduleCategory category,
            @Param("labelId") Long labelId,
            @Param("query") String query,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // 통계
    long countByCompanyId(Long companyId);

    long countByCompanyIdAndCategory(Long companyId, ScheduleCategory category);

    // 특정 라벨을 사용하는 일정 수
    long countByLabelId(Long labelId);
}