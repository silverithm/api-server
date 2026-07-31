package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.PlazaPostView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlazaPostViewRepository extends JpaRepository<PlazaPostView, Long> {

    boolean existsByPostIdAndUserId(Long postId, String userId);
}
