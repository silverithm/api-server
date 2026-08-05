package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.DispatchSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchSettingRepository extends JpaRepository<DispatchSetting, Long> {

    Optional<DispatchSetting> findByCompanyId(Long companyId);
}
