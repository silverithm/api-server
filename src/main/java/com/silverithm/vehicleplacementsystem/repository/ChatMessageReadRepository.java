package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ChatMessageRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageReadRepository extends JpaRepository<ChatMessageRead, Long> {

    // 메시지를 읽은 사용자 목록
    List<ChatMessageRead> findByMessageIdOrderByReadAtDesc(Long messageId);

    // 특정 사용자의 메시지 읽음 확인
    Optional<ChatMessageRead> findByMessageIdAndUserId(Long messageId, String userId);

    // 메시지를 읽은 사용자 수
    long countByMessageId(Long messageId);

    // 특정 사용자가 특정 메시지를 읽었는지 확인
    boolean existsByMessageIdAndUserId(Long messageId, String userId);

    // 채팅방의 특정 메시지까지 일괄 읽음 처리를 위한 메시지 ID 조회
    @Query("SELECT m.id FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.id <= :lastMessageId " +
           "AND NOT EXISTS (SELECT 1 FROM ChatMessageRead r WHERE r.message.id = m.id AND r.userId = :userId)")
    List<Long> findUnreadMessageIds(
            @Param("chatRoomId") Long chatRoomId,
            @Param("lastMessageId") Long lastMessageId,
            @Param("userId") String userId);

    // 사용자의 읽음 기록 삭제 (채팅방 나갈 때)
    @Modifying
    @Query("DELETE FROM ChatMessageRead r " +
           "WHERE r.message.chatRoom.id = :chatRoomId AND r.userId = :userId")
    void deleteByUserIdAndChatRoomId(
            @Param("chatRoomId") Long chatRoomId,
            @Param("userId") String userId);
}
