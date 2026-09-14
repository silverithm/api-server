package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 방의 마지막 메시지 시각만 갱신한다 — 메시지를 저장하기 **전에** 부른다.
     *
     * <p>메시지 INSERT는 외래키 검사로 방 행에 공유 잠금을 잡고, 그 뒤 방을 UPDATE하면
     * 배타 잠금으로 올리려 한다. 사진 여러 장이 같은 방에 동시에 오면 모두 공유 잠금을 쥔 채
     * 서로의 배타 잠금을 기다려 교착이 났다(운영 "Deadlock found when trying to get lock").
     * 배타 잠금을 먼저 잡으면 뒤따르는 INSERT의 공유 잠금은 내 것과 겹치므로 교착이 없다.
     * 엔티티를 고쳐 flush하면 열일곱 컬럼이 통째로 UPDATE되던 것도 한 컬럼으로 줄어든다.
     */
    @Modifying
    @Query("UPDATE ChatRoom r SET r.lastMessageAt = :at WHERE r.id = :roomId")
    int touchLastMessageAt(@Param("roomId") Long roomId, @Param("at") LocalDateTime at);

    // 회사별 활성 채팅방 조회 (최신 메시지순)
    @Query("SELECT DISTINCT cr FROM ChatRoom cr " +
           "JOIN cr.participants p " +
           "WHERE cr.company.id = :companyId " +
           "AND cr.status = 'ACTIVE' " +
           "AND ((:memberId IS NOT NULL AND p.memberId = :memberId) OR (:appUserId IS NOT NULL AND p.appUserId = :appUserId)) " +
           "AND p.isActive = true " +
           "ORDER BY COALESCE(cr.lastMessageAt, cr.createdAt) DESC")
    List<ChatRoom> findActiveRoomsByCompanyIdAndPerson(
            @Param("companyId") Long companyId,
            @Param("memberId") Long memberId,
            @Param("appUserId") Long appUserId);

    // 회사별 전체 채팅방 조회
    List<ChatRoom> findByCompanyIdAndStatusOrderByLastMessageAtDesc(
            Long companyId, ChatRoom.ChatRoomStatus status);

    // 사용자가 참여 중인 채팅방 수
    @Query("SELECT COUNT(DISTINCT cr) FROM ChatRoom cr " +
           "JOIN cr.participants p " +
           "WHERE p.isActive = true " +
           "AND cr.status = 'ACTIVE' " +
           "AND ((:memberId IS NOT NULL AND p.memberId = :memberId) OR (:appUserId IS NOT NULL AND p.appUserId = :appUserId)) ")
    long countActiveRoomsByPerson(
            @Param("memberId") Long memberId,
            @Param("appUserId") Long appUserId);

    // 채팅방 검색
    @Query("SELECT cr FROM ChatRoom cr " +
           "JOIN cr.participants p " +
           "WHERE cr.company.id = :companyId " +
           "AND cr.status = 'ACTIVE' " +
           "AND ((:memberId IS NOT NULL AND p.memberId = :memberId) OR (:appUserId IS NOT NULL AND p.appUserId = :appUserId)) " +
           "AND p.isActive = true " +
           "AND cr.name LIKE %:query% " +
           "ORDER BY COALESCE(cr.lastMessageAt, cr.createdAt) DESC")
    List<ChatRoom> searchByName(
            @Param("companyId") Long companyId,
            @Param("memberId") Long memberId,
            @Param("appUserId") Long appUserId,
            @Param("query") String query);
}
