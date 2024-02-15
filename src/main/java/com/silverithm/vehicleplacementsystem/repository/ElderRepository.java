package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Elderly;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElderRepository extends JpaRepository<Elderly, Long> {
}
