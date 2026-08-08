package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.MemberJoinRequest;
import com.silverithm.vehicleplacementsystem.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberJoinRequestRepository extends JpaRepository<MemberJoinRequest, Long> {
    
    List<MemberJoinRequest> findByStatusOrderByCreatedAtDesc(MemberJoinRequest.RequestStatus status);
    
    List<MemberJoinRequest> findAllByOrderByCreatedAtDesc();
    
    List<MemberJoinRequest> findByCompanyOrderByCreatedAtDesc(Company company);
    
    List<MemberJoinRequest> findByCompanyAndStatusOrderByCreatedAtDesc(Company company, MemberJoinRequest.RequestStatus status);
    
    /**
     * 같은 기관에 이미 올라와 있는 같은 상태의 신청 (아이디 또는 이메일이 같으면 같은 사람으로 본다).
     *
     * 여러 건이 나올 수 있어 목록으로 받는다 — 이 확인을 넣기 전에 쌓인 중복이 남아 있다.
     * 상태는 파라미터로 받는다. 중첩 enum을 JPQL 안에 상수로 적으면 Hibernate가 경로를 해석하지 못해
     * 리포지토리 생성 단계에서 애플리케이션이 아예 뜨지 않는다.
     */
    @Query("SELECT jr FROM MemberJoinRequest jr WHERE jr.company = :company "
            + "AND jr.status = :status "
            + "AND (jr.username = :username OR jr.email = :email) "
            + "ORDER BY jr.createdAt ASC")
    List<MemberJoinRequest> findDuplicatesByStatus(@Param("company") Company company,
                                                   @Param("status") MemberJoinRequest.RequestStatus status,
                                                   @Param("username") String username,
                                                   @Param("email") String email);

    Optional<MemberJoinRequest> findByUsername(String username);

    Optional<MemberJoinRequest> findByEmail(String email);
    
    @Query("SELECT jr FROM MemberJoinRequest jr WHERE jr.createdAt BETWEEN :startDate AND :endDate ORDER BY jr.createdAt DESC")
    List<MemberJoinRequest> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT jr FROM MemberJoinRequest jr WHERE jr.status = :status AND jr.createdAt BETWEEN :startDate AND :endDate ORDER BY jr.createdAt DESC")
    List<MemberJoinRequest> findByStatusAndCreatedAtBetween(
            @Param("status") MemberJoinRequest.RequestStatus status,
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT jr FROM MemberJoinRequest jr WHERE jr.company = :company AND jr.createdAt BETWEEN :startDate AND :endDate ORDER BY jr.createdAt DESC")
    List<MemberJoinRequest> findByCompanyAndCreatedAtBetween(
            @Param("company") Company company,
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT jr FROM MemberJoinRequest jr WHERE jr.company = :company AND jr.status = :status AND jr.createdAt BETWEEN :startDate AND :endDate ORDER BY jr.createdAt DESC")
    List<MemberJoinRequest> findByCompanyAndStatusAndCreatedAtBetween(
            @Param("company") Company company,
            @Param("status") MemberJoinRequest.RequestStatus status,
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    boolean existsByCompanyAndUsername(Company company, String username);
    
    boolean existsByCompanyAndEmail(Company company, String email);
    
    Long countByStatus(MemberJoinRequest.RequestStatus status);
    
    Long countByCompanyAndStatus(Company company, MemberJoinRequest.RequestStatus status);
} 