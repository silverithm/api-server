package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.MeetingMinutesTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingMinutesTemplateRepository extends JpaRepository<MeetingMinutesTemplate, Long> {

    List<MeetingMinutesTemplate> findByCompanyIdOrderBySortOrderAscIdAsc(Long companyId);

    /** 회사당 최대 1개만 존재해야 한다(서비스 계층이 보장) */
    Optional<MeetingMinutesTemplate> findByCompanyIdAndIsDefaultTrue(Long companyId);

    Optional<MeetingMinutesTemplate> findByIdAndCompanyId(Long id, Long companyId);
}
