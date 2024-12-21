package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.DispatchHistory;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DispatchHistoryRepository extends JpaRepository<DispatchHistory, Long> {
    List<DispatchHistory> findAllByOrderByCreatedAtDesc();
    List<DispatchHistory> findAllByUsernameOrderByCreatedAtDesc(String username);

    Page<DispatchHistory> findAllByUsername(String username, Pageable pageable);
}