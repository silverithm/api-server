package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.PaymentFailureLog;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentFailureLogRepository extends JpaRepository<PaymentFailureLog, Long> {
    
    Page<PaymentFailureLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    @Query("SELECT p FROM PaymentFailureLog p WHERE p.user.id = :userId AND p.failureReason = :reason ORDER BY p.createdAt DESC")
    Page<PaymentFailureLog> findByUserIdAndFailureReasonOrderByCreatedAtDesc(@Param("userId") Long userId, 
                                                                              @Param("reason") PaymentFailureReason reason, 
                                                                              Pageable pageable);
    
    @Query("SELECT p FROM PaymentFailureLog p WHERE p.user.id = :userId AND p.createdAt BETWEEN :startDate AND :endDate ORDER BY p.createdAt DESC")
    Page<PaymentFailureLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(@Param("userId") Long userId,
                                                                                 @Param("startDate") LocalDateTime startDate,
                                                                                 @Param("endDate") LocalDateTime endDate,
                                                                                 Pageable pageable);
    
    @Query("SELECT p FROM PaymentFailureLog p WHERE p.user.id = :userId AND p.failureReason = :reason ORDER BY p.createdAt DESC")
    List<PaymentFailureLog> findRecentFailuresByUserAndReason(@Param("userId") Long userId, 
                                                               @Param("reason") PaymentFailureReason reason, 
                                                               Pageable pageable);
    
    @Query("SELECT COUNT(p) FROM PaymentFailureLog p WHERE p.user.id = :userId AND p.failureReason = :reason AND p.createdAt >= :sinceDate")
    Long countRecentFailuresByUserAndReason(@Param("userId") Long userId, 
                                            @Param("reason") PaymentFailureReason reason, 
                                            @Param("sinceDate") LocalDateTime sinceDate);
}