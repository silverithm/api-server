package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest;
import com.silverithm.vehicleplacementsystem.entity.ApprovalRequest.ApprovalStatus;
import com.silverithm.vehicleplacementsystem.entity.ApprovalStep;
import com.silverithm.vehicleplacementsystem.entity.ApprovalViewerType;
import com.silverithm.vehicleplacementsystem.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * 열람 가능 조건 — 다음 중 하나면 그 문서를 볼 수 있다.
     * <ol>
     *   <li>기관 관리자 (:isAdmin)</li>
     *   <li>기안자 본인</li>
     *   <li>결재선에 이름이 오른 사람</li>
     *   <li>문서 열람 대상으로 개인 지정됐거나, 지정된 직책을 가진 사람</li>
     * </ol>
     * 값이 없는 파라미터는 -1을 넘겨 어디에도 매칭되지 않게 한다(널 비교 회피).
     */
    String VIEWABLE_CONDITION =
            "AND (:isAdmin = TRUE "
            + "OR a.requesterId = :callerLegacyId "
            + "OR EXISTS (SELECT 1 FROM ApprovalStep s WHERE s.approvalRequest = a "
            + "           AND s.approverType = :callerStepType AND s.approverRefId = :callerRefId) "
            + "OR EXISTS (SELECT 1 FROM ApprovalRequestViewer v WHERE v.approvalRequest = a "
            + "           AND ((v.viewerType = :callerViewerType AND v.refId = :callerRefId) "
            + "                OR (v.viewerType = ApprovalViewerType.POSITION "
            + "                    AND v.refId = :callerPositionId)))) ";

    /**
     * 결재함 목록 — 상태·기간·양식·대분류로 거르고, 검색어는 제목/기안자/양식명/첨부파일명/
     * 본문/결재자명/열람 대상명을 함께 훑는다. 검색어와 선택 필터는 null이면 무시된다.
     */
    @Query("SELECT DISTINCT a FROM ApprovalRequest a LEFT JOIN a.template t "
           + "WHERE a.company.id = :companyId "
           + "AND a.status <> ApprovalStatus.DRAFT "
           + "AND (:status IS NULL OR a.status = :status) "
           + "AND a.createdAt >= :startDate AND a.createdAt <= :endDate "
           + "AND (:templateId IS NULL OR t.id = :templateId) "
           + "AND (:category IS NULL "
           + "     OR (:category = '__NONE__' AND (t.category IS NULL OR t.category = '')) "
           + "     OR t.category = :category) "
           + "AND (:searchQuery IS NULL "
           + "     OR LOWER(a.title) LIKE LOWER(CONCAT('%', :searchQuery, '%')) "
           + "     OR LOWER(a.requesterName) LIKE LOWER(CONCAT('%', :searchQuery, '%')) "
           + "     OR LOWER(t.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')) "
           + "     OR LOWER(a.docNumberDisplay) LIKE LOWER(CONCAT('%', :searchQuery, '%')) "
           + "     OR LOWER(a.externalDocNumber) LIKE LOWER(CONCAT('%', :searchQuery, '%')) "
           + "     OR LOWER(a.attachmentFileName) LIKE LOWER(CONCAT('%', :searchQuery, '%')) "
           + "     OR LOWER(a.formData) LIKE LOWER(CONCAT('%', :searchQuery, '%')) "
           + "     OR EXISTS (SELECT 1 FROM ApprovalStep s2 WHERE s2.approvalRequest = a "
           + "                AND LOWER(s2.approverName) LIKE LOWER(CONCAT('%', :searchQuery, '%'))) "
           + "     OR EXISTS (SELECT 1 FROM ApprovalRequestViewer v2 WHERE v2.approvalRequest = a "
           + "                AND LOWER(v2.viewerName) LIKE LOWER(CONCAT('%', :searchQuery, '%')))) "
           + VIEWABLE_CONDITION
           + "ORDER BY a.createdAt DESC")
    List<ApprovalRequest> searchViewable(
            @Param("companyId") Long companyId,
            @Param("status") ApprovalStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("templateId") Long templateId,
            @Param("category") String category,
            @Param("searchQuery") String searchQuery,
            @Param("isAdmin") boolean isAdmin,
            @Param("callerLegacyId") String callerLegacyId,
            @Param("callerStepType") ApprovalStep.ApproverType callerStepType,
            @Param("callerViewerType") ApprovalViewerType callerViewerType,
            @Param("callerRefId") Long callerRefId,
            @Param("callerPositionId") Long callerPositionId
    );

    /** 결재함 탭 카운트 — 목록과 같은 열람 범위로 상태별 건수를 센다 (기간·검색은 적용하지 않는다) */
    @Query("SELECT a.status, COUNT(DISTINCT a) FROM ApprovalRequest a "
           + "WHERE a.company.id = :companyId "
           + "AND a.status <> ApprovalStatus.DRAFT "
           + VIEWABLE_CONDITION
           + "GROUP BY a.status")
    List<Object[]> countViewableByStatus(
            @Param("companyId") Long companyId,
            @Param("isAdmin") boolean isAdmin,
            @Param("callerLegacyId") String callerLegacyId,
            @Param("callerStepType") ApprovalStep.ApproverType callerStepType,
            @Param("callerViewerType") ApprovalViewerType callerViewerType,
            @Param("callerRefId") Long callerRefId,
            @Param("callerPositionId") Long callerPositionId
    );

    /**
     * 이미 이관된 원본 문서번호들 — 두 번 올려도 중복으로 쌓이지 않게.
     * 수천 건짜리 색인을 줄마다 exists로 물으면 그 수만큼 쿼리가 나가므로 한 번에 걷어온다.
     */
    @Query("SELECT a.externalDocNumber FROM ApprovalRequest a "
           + "WHERE a.company.id = :companyId AND a.externalDocNumber IN :docNumbers")
    List<String> findExistingExternalDocNumbers(
            @Param("companyId") Long companyId,
            @Param("docNumbers") Collection<String> docNumbers);

    Long countByCompanyIdAndStatus(Long companyId, ApprovalStatus status);

    // 특정 템플릿을 사용하는 결재 요청이 있는지 확인
    boolean existsByTemplateId(Long templateId);

    // 특정 템플릿을 사용하는 결재 요청 수
    Long countByTemplateId(Long templateId);

    // 특정 템플릿을 사용하는 결재 요청 일괄 삭제
    void deleteByTemplateId(Long templateId);
}
