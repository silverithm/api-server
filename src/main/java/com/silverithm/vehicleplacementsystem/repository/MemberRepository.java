package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    
    Optional<Member> findByUsername(String username);
    
    Optional<Member> findByEmail(String email);
    
    List<Member> findByRole(Member.Role role);
    
    List<Member> findByStatus(Member.MemberStatus status);
    
    List<Member> findByRoleAndStatus(Member.Role role, Member.MemberStatus status);
    
    @Query("SELECT m FROM Member m WHERE m.name LIKE %:name% ORDER BY m.createdAt DESC")
    List<Member> findByNameContaining(@Param("name") String name);
    
    @Query("SELECT m FROM Member m WHERE m.department = :department ORDER BY m.createdAt DESC")
    List<Member> findByDepartment(@Param("department") String department);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    Long countByRole(Member.Role role);
    
    Long countByStatus(Member.MemberStatus status);
} 