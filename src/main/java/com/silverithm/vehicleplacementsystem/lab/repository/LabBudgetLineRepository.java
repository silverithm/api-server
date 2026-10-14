package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabBudgetLine;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBudgetLineRepository extends JpaRepository<LabBudgetLine, Long> {

    List<LabBudgetLine> findByBudgetId(Long budgetId);

    /** 한 해 예산 여럿의 줄을 한 번에 읽는다(예산마다 쿼리를 내지 않으려고) */
    List<LabBudgetLine> findByBudgetIdIn(Collection<Long> budgetIds);
}
