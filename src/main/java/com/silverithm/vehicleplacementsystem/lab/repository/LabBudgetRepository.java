package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabBudget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBudgetRepository extends JpaRepository<LabBudget, Long> {

    List<LabBudget> findByCompanyIdAndFiscalYearOrderByRevisionAsc(Long companyId, short fiscalYear);

    Optional<LabBudget> findByCompanyIdAndFiscalYearAndRevision(Long companyId, short fiscalYear, short revision);

    boolean existsByCompanyIdAndFiscalYearAndRevisionGreaterThan(Long companyId, short fiscalYear, short revision);

    Optional<LabBudget> findByIdAndCompanyId(Long id, Long companyId);
}
