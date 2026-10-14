package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabMedicineStock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabMedicineStockRepository extends JpaRepository<LabMedicineStock, Long> {

    Optional<LabMedicineStock> findByIdAndCompanyId(Long id, Long companyId);

    List<LabMedicineStock> findByCompanyIdOrderByExpiresOnAscIdAsc(Long companyId);

    List<LabMedicineStock> findByCompanyIdAndDisposedOnIsNullOrderByExpiresOnAscIdAsc(Long companyId);

    List<LabMedicineStock> findByCompanyIdAndIdIn(Long companyId, java.util.Collection<Long> ids);
}
