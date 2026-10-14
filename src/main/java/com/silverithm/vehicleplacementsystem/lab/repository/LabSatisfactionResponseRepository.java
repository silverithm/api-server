package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabSatisfactionResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabSatisfactionResponseRepository extends JpaRepository<LabSatisfactionResponse, Long> {

    List<LabSatisfactionResponse> findByCompanyIdAndSurveyDateBetweenOrderBySurveyDateDescIdDesc(Long companyId,
                                                                                                 LocalDate from,
                                                                                                 LocalDate to);

    List<LabSatisfactionResponse> findByCompanyIdAndKindAndSurveyDateBetweenOrderBySurveyDateDescIdDesc(
            Long companyId, String kind, LocalDate from, LocalDate to);

    Optional<LabSatisfactionResponse> findByIdAndCompanyId(Long id, Long companyId);
}
