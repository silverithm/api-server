package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabProgramFeedback;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabProgramFeedbackRepository extends JpaRepository<LabProgramFeedback, Long> {

    List<LabProgramFeedback> findByCompanyIdAndHalfInOrderByCollectedOnAscIdAsc(Long companyId,
                                                                               Collection<String> halves);

    Optional<LabProgramFeedback> findByIdAndCompanyId(Long id, Long companyId);
}
