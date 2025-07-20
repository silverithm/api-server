package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.FreeSubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FreeSubscriptionHistoryRepository extends JpaRepository<FreeSubscriptionHistory, Long> {
    boolean existsByUserId(Long userId);
}