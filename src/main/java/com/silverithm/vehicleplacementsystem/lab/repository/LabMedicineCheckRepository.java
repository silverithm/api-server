package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicineCheck;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabMedicineCheckRepository extends JpaRepository<LabMedicineCheck, Long> {

    Optional<LabMedicineCheck> findByIdAndCompanyId(Long id, Long companyId);

    Optional<LabMedicineCheck> findByCompanyIdAndCheckedOn(Long companyId, LocalDate checkedOn);

    List<LabMedicineCheck> findByCompanyIdAndCheckedOnBetweenOrderByCheckedOnAscIdAsc(Long companyId, LocalDate from,
                                                                                    LocalDate to);
}
