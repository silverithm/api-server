package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetingMinutesTemplateRepository extends JpaRepository<MeetingMinutesTemplate, Long> {

    Optional<MeetingMinutesTemplate> findByCompanyId(Long companyId);
}
