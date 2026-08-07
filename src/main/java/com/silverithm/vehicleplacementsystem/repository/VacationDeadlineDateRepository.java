package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.VacationDeadlineDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacationDeadlineDateRepository extends JpaRepository<VacationDeadlineDate, Long> {

    List<VacationDeadlineDate> findByCompanyIdOrderByTargetMonthAsc(Long companyId);

    Optional<VacationDeadlineDate> findByCompanyIdAndTargetMonth(Long companyId, String targetMonth);

    void deleteByCompanyIdAndTargetMonth(Long companyId, String targetMonth);
}
