package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByCompanyOrderByCreatedAtDesc(Company company);

    List<ApprovalRequest> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<ApprovalRequest> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, ApprovalStatus status);

    List<ApprovalRequest> findByRequesterIdOrderByCreatedAtDesc(String requesterId);

    @Query("SELECT a FROM ApprovalRequest a WHERE a.company.id = :companyId " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND a.createdAt >= :startDate AND a.createdAt <= :endDate " +
           "ORDER BY a.createdAt DESC")
    List<ApprovalRequest> findByCompanyIdAndFilters(
            @Param("companyId") Long companyId,
            @Param("status") ApprovalStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT a FROM ApprovalRequest a WHERE a.company.id = :companyId " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND a.createdAt >= :startDate AND a.createdAt <= :endDate " +
           "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :searchQuery, '%')) " +
           "OR LOWER(a.requesterName) LIKE LOWER(CONCAT('%', :searchQuery, '%'))) " +
           "ORDER BY a.createdAt DESC")
    List<ApprovalRequest> findByCompanyIdAndFiltersWithSearch(
            @Param("companyId") Long companyId,
            @Param("status") ApprovalStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("searchQuery") String searchQuery
    );

    Long countByCompanyIdAndStatus(Long companyId, ApprovalStatus status);

    // 특정 템플릿을 사용하는 결재 요청이 있는지 확인
    boolean existsByTemplateId(Long templateId);

    // 특정 템플릿을 사용하는 결재 요청 수
    Long countByTemplateId(Long templateId);

    // 특정 템플릿을 사용하는 결재 요청 일괄 삭제
    void deleteByTemplateId(Long templateId);
}
