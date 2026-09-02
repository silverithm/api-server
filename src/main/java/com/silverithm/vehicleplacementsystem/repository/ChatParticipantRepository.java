package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    // 채팅방의 활성 참가자 목록
    List<ChatParticipant> findByChatRoomIdAndIsActiveTrueOrderByJoinedAtAsc(Long chatRoomId);

    // 채팅방의 전체 참가자 목록 (비활성 포함)
    List<ChatParticipant> findByChatRoomIdOrderByJoinedAtAsc(Long chatRoomId);

    // 특정 사용자의 채팅방 참가 정보
    @Query("SELECT p FROM ChatParticipant p WHERE p.chatRoom.id = :chatRoomId "
            + "AND ((:memberId IS NOT NULL AND p.memberId = :memberId) "
            + "OR (:appUserId IS NOT NULL AND p.appUserId = :appUserId))")
    Optional<ChatParticipant> findByRoomAndPerson(@Param("chatRoomId") Long chatRoomId,
                                                  @Param("memberId") Long memberId,
                                                  @Param("appUserId") Long appUserId);

    // 활성 참가자인지 확인
    @Query("SELECT p FROM ChatParticipant p WHERE p.chatRoom.id = :chatRoomId AND p.isActive = true "
            + "AND ((:memberId IS NOT NULL AND p.memberId = :memberId) "
            + "OR (:appUserId IS NOT NULL AND p.appUserId = :appUserId))")
    Optional<ChatParticipant> findActiveByRoomAndPerson(@Param("chatRoomId") Long chatRoomId,
                                                        @Param("memberId") Long memberId,
                                                        @Param("appUserId") Long appUserId);

    /**
     * 여러 방에서의 내 참가 정보를 한 번에.
     *
     * 방 목록이 방마다 findActiveByRoomAndPerson을 부르면 방 개수만큼 쿼리가 나간다.
     */
    @Query("SELECT p FROM ChatParticipant p WHERE p.chatRoom.id IN :chatRoomIds AND p.isActive = true "
            + "AND ((:memberId IS NOT NULL AND p.memberId = :memberId) "
            + "OR (:appUserId IS NOT NULL AND p.appUserId = :appUserId))")
    List<ChatParticipant> findActiveByRoomsAndPerson(@Param("chatRoomIds") List<Long> chatRoomIds,
                                                     @Param("memberId") Long memberId,
                                                     @Param("appUserId") Long appUserId);

    // 채팅방의 활성 참가자 수
    long countByChatRoomIdAndIsActiveTrue(Long chatRoomId);

    // 사용자가 특정 채팅방의 관리자인지 확인
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM ChatParticipant p " +
           "WHERE p.chatRoom.id = :chatRoomId " +
           "AND p.userId = :userId " +
           "AND p.role = 'ADMIN' " +
           "AND p.isActive = true")
    boolean isRoomAdmin(@Param("chatRoomId") Long chatRoomId, @Param("userId") String userId);

    // 특정 사용자의 모든 채팅방 참가 정보 (계정 삭제 시 사용)
    @Query("SELECT p FROM ChatParticipant p WHERE p.isActive = true "
            + "AND ((:memberId IS NOT NULL AND p.memberId = :memberId) "
            + "OR (:appUserId IS NOT NULL AND p.appUserId = :appUserId))")
    List<ChatParticipant> findActiveByPerson(@Param("memberId") Long memberId,
                                             @Param("appUserId") Long appUserId);

    // 채팅방에서 메시지를 읽지 않은 참가자 목록
    @Query("SELECT p FROM ChatParticipant p " +
           "WHERE p.chatRoom.id = :chatRoomId " +
           "AND p.isActive = true " +
           "AND (p.lastReadMessageId IS NULL OR p.lastReadMessageId < :messageId)")
    List<ChatParticipant> findUnreadParticipants(
            @Param("chatRoomId") Long chatRoomId,
            @Param("messageId") Long messageId);
}
