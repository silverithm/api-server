package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 채팅방의 메시지 목록 (페이지네이션, 최신순)
    // 답글 원문은 목록에 그대로 실려 나가므로 함께 가져온다 — 지연 로딩으로 두면 답글 하나마다 SELECT가 더 나간다.
    @EntityGraph(attributePaths = {"replyTo"})
    Page<ChatMessage> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    /**
     * 여러 방의 마지막 메시지를 한 번에.
     *
     * 방 목록 화면은 방마다 마지막 메시지를 보여준다. 방마다 따로 조회하면 방 개수만큼 쿼리가 나간다.
     */
    @EntityGraph(attributePaths = {"replyTo"})
    @Query("SELECT m FROM ChatMessage m WHERE m.id IN (" +
           "  SELECT MAX(m2.id) FROM ChatMessage m2 WHERE m2.chatRoom.id IN :chatRoomIds GROUP BY m2.chatRoom.id)")
    List<ChatMessage> findLastMessagesOfRooms(@Param("chatRoomIds") List<Long> chatRoomIds);

    /**
     * 여러 방의 안 읽은 메시지 수를 한 번에.
     *
     * 방마다 '내 참가 정보 조회 + 카운트'로 두 번씩 나가던 것을 참가 행과 조인해 한 번으로 줄인다.
     * 기준(마지막 읽은 메시지 이후, 내가 보낸 것 제외)은 countUnreadMessages와 같다.
     */
    @Query("SELECT m.chatRoom.id, COUNT(m) FROM ChatMessage m " +
           "JOIN ChatParticipant p ON p.chatRoom.id = m.chatRoom.id " +
           "WHERE p.id IN :participantIds " +
           "AND (p.lastReadMessageId IS NULL OR m.id > p.lastReadMessageId) " +
           "AND m.senderId <> :userId " +
           "GROUP BY m.chatRoom.id")
    List<Object[]> countUnreadByParticipants(@Param("participantIds") List<Long> participantIds,
                                             @Param("userId") String userId);

    // 채팅방의 최근 메시지 목록 (제한 수)
    List<ChatMessage> findTop50ByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);

    // 특정 메시지 ID 이전 메시지 조회 (더 불러오기)
    @EntityGraph(attributePaths = {"replyTo"})
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.id < :beforeId " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessage> findMessagesBefore(
            @Param("chatRoomId") Long chatRoomId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // 특정 메시지 ID 이후 메시지 조회 (오름차순 — 중심 메시지에 가장 가까운 것부터)
    // "주변 조회"(around)에서 이후 구간을 가져올 때만 쓴다. 목록 조회는 항상 최신순(DESC)이라
    // 이 순서가 필요한 다른 곳은 없다.
    @EntityGraph(attributePaths = {"replyTo"})
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.id > :afterId " +
           "ORDER BY m.createdAt ASC")
    Page<ChatMessage> findMessagesAfter(
            @Param("chatRoomId") Long chatRoomId,
            @Param("afterId") Long afterId,
            Pageable pageable);

    // 채팅방의 최신 메시지
    Optional<ChatMessage> findFirstByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);

    // 채팅방의 공유된 미디어 (이미지, 파일)
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.type IN ('IMAGE', 'FILE') " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> findSharedMedia(@Param("chatRoomId") Long chatRoomId);

    // 타입별 미디어 조회
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.type = :type " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> findByTypeAndChatRoomId(
            @Param("chatRoomId") Long chatRoomId,
            @Param("type") ChatMessage.MessageType type);

    // 안읽은 메시지 수
    @Query("SELECT COUNT(m) FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.id > :lastReadMessageId " +
           "AND m.senderId != :userId")
    long countUnreadMessages(
            @Param("chatRoomId") Long chatRoomId,
            @Param("lastReadMessageId") Long lastReadMessageId,
            @Param("userId") String userId);

    // 특정 ID 이후의 메시지 수 (lastReadMessageId가 null인 경우)
    @Query("SELECT COUNT(m) FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.senderId != :userId")
    long countAllUnreadMessages(
            @Param("chatRoomId") Long chatRoomId,
            @Param("userId") String userId);

    // 메시지 검색
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.isDeleted = false " +
           "AND m.content LIKE %:query% " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> searchMessages(
            @Param("chatRoomId") Long chatRoomId,
            @Param("query") String query);
}
