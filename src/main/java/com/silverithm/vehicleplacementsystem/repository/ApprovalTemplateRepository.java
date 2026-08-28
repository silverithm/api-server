package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, Long> {

    List<ApprovalTemplate> findByCompanyOrderByCreatedAtDesc(Company company);

    List<ApprovalTemplate> findByCompanyAndIsActiveTrueOrderByCreatedAtDesc(Company company);

    List<ApprovalTemplate> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<ApprovalTemplate> findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(Long companyId);

    /** 관리자가 드래그로 정한 순서(오름차순) 우선, 그다음 최신 등록순 */
    List<ApprovalTemplate> findByCompanyIdOrderBySortOrderAscCreatedAtDesc(Long companyId);

    List<ApprovalTemplate> findByCompanyIdAndIsActiveTrueOrderBySortOrderAscCreatedAtDesc(Long companyId);

    Optional<ApprovalTemplate> findFirstByCompanyIdOrderBySortOrderDesc(Long companyId);
}