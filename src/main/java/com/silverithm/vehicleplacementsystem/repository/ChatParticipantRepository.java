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

    /**
     * 여러 방의 참가자와 그 사람의 프로필 사진을 **한 번에** 가져온다.
     *
     * 목록에 카톡처럼 얼굴을 겹쳐 보여주려면 참가자와 사진이 필요한데,
     * 참가자를 따로 묻고 사진을 또 따로 물으면 조회가 두 번 는다.
     * 사진은 직원(Member)이나 관리자(AppUser) 쪽에 있으므로 여기서 이어 붙인다.
     * (참가자는 두 표를 번호로만 가리켜서 연관관계가 없다 — 그래서 ON으로 직접 잇는다)
     *
     * 돌려주는 각 줄: [방 번호, 사용자 식별자, 이름, 사진 URL]
     * 들어온 순서(joinedAt)대로 준다.
     */
    @Query("SELECT p.chatRoom.id, p.userId, p.userName, "
            + "COALESCE(m.profileImageUrl, a.profileImageUrl) "
            + "FROM ChatParticipant p "
            + "LEFT JOIN Member m ON m.id = p.memberId "
            + "LEFT JOIN AppUser a ON a.id = p.appUserId "
            + "WHERE p.chatRoom.id IN :chatRoomIds AND p.isActive = true "
            + "ORDER BY p.chatRoom.id, p.joinedAt")
    List<Object[]> findAvatarRowsByRoomIds(@Param("chatRoomIds") List<Long> chatRoomIds);

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
