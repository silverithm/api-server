package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabTransportVehicle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabTransportVehicleRepository extends JpaRepository<LabTransportVehicle, Long> {

    List<LabTransportVehicle> findByCompanyIdOrderByActiveDescVehicleNoAscIdAsc(Long companyId);

    Optional<LabTransportVehicle> findByCompanyIdAndVehicleNo(Long companyId, String vehicleNo);

    Optional<LabTransportVehicle> findByIdAndCompanyId(Long id, Long companyId);
}
