package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.DispatchDayOverride;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchDayOverrideRepository extends JpaRepository<DispatchDayOverride, Long> {

    Optional<DispatchDayOverride> findByCompanyIdAndDispatchDate(Long companyId, LocalDate dispatchDate);

    /** 달력·목록처럼 여러 날을 한 번에 그리는 화면용 */
    List<DispatchDayOverride> findByCompanyIdAndDispatchDateBetween(Long companyId, LocalDate start, LocalDate end);
}
