package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.VacationDeadlineSetting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacationDeadlineSettingRepository extends JpaRepository<VacationDeadlineSetting, Long> {

    Optional<VacationDeadlineSetting> findByCompanyId(Long companyId);

    List<VacationDeadlineSetting> findByEnabledTrue();
}
