package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 회사별 활성 채팅방 조회 (최신 메시지순)
    @Query("SELECT DISTINCT cr FROM ChatRoom cr " +
           "JOIN cr.participants p " +
           "WHERE cr.company.id = :companyId " +
           "AND cr.status = 'ACTIVE' " +
           "AND p.userId = :userId " +
           "AND p.isActive = true " +
           "ORDER BY COALESCE(cr.lastMessageAt, cr.createdAt) DESC")
    List<ChatRoom> findActiveRoomsByCompanyIdAndUserId(
            @Param("companyId") Long companyId,
            @Param("userId") String userId);

    // 회사별 전체 채팅방 조회
    List<ChatRoom> findByCompanyIdAndStatusOrderByLastMessageAtDesc(
            Long companyId, ChatRoom.ChatRoomStatus status);

    // 사용자가 참여 중인 채팅방 수
    @Query("SELECT COUNT(DISTINCT cr) FROM ChatRoom cr " +
           "JOIN cr.participants p " +
           "WHERE p.userId = :userId " +
           "AND p.isActive = true " +
           "AND cr.status = 'ACTIVE'")
    long countActiveRoomsByUserId(@Param("userId") String userId);

    // 채팅방 검색
    @Query("SELECT cr FROM ChatRoom cr " +
           "JOIN cr.participants p " +
           "WHERE cr.company.id = :companyId " +
           "AND cr.status = 'ACTIVE' " +
           "AND p.userId = :userId " +
           "AND p.isActive = true " +
           "AND cr.name LIKE %:query% " +
           "ORDER BY COALESCE(cr.lastMessageAt, cr.createdAt) DESC")
    List<ChatRoom> searchByName(
            @Param("companyId") Long companyId,
            @Param("userId") String userId,
            @Param("query") String query);
}
