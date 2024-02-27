package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
import com.silverithm.vehicleplacementsystem.repository.querydsl.LinkDistanceRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LinkDistanceRepository extends JpaRepository<LinkDistance, Long>, LinkDistanceRepositoryCustom {
}
