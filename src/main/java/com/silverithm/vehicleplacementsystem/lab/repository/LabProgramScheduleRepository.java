package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabProgramSchedule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabProgramScheduleRepository extends JpaRepository<LabProgramSchedule, Long> {

    Optional<LabProgramSchedule> findByCompanyIdAndScheduleMonth(Long companyId, String scheduleMonth);

    /** 'YYYY-' 접두로 그 해의 달 전부, 달 오름차순 */
    List<LabProgramSchedule> findByCompanyIdAndScheduleMonthStartingWithOrderByScheduleMonthAsc(Long companyId,
                                                                                                String yearPrefix);

    Optional<LabProgramSchedule> findByIdAndCompanyId(Long id, Long companyId);
}
