package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ScheduleLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleLabelRepository extends JpaRepository<ScheduleLabel, Long> {

    List<ScheduleLabel> findByCompanyIdOrderByNameAsc(Long companyId);

    List<ScheduleLabel> findByCompanyId(Long companyId);

    boolean existsByCompanyIdAndName(Long companyId, String name);

    boolean existsByCompanyIdAndNameAndIdNot(Long companyId, String name, Long id);
}
