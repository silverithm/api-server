package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategorySetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduleCategorySettingRepository extends JpaRepository<ScheduleCategorySetting, Long> {

    List<ScheduleCategorySetting> findByCompanyId(Long companyId);

    Optional<ScheduleCategorySetting> findByCompanyIdAndCategory(Long companyId, ScheduleCategory category);
}
