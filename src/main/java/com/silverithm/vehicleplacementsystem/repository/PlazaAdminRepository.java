package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.PlazaAdmin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlazaAdminRepository extends JpaRepository<PlazaAdmin, Long> {

    boolean existsByEmail(String email);

    Optional<PlazaAdmin> findByEmail(String email);
}
