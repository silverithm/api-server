package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 채팅방의 메시지 목록 (페이지네이션, 최신순)
    Page<ChatMessage> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    // 채팅방의 최근 메시지 목록 (제한 수)
    List<ChatMessage> findTop50ByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);

    // 특정 메시지 ID 이전 메시지 조회 (더 불러오기)
    @Query("SELECT m FROM ChatMessage m " +
           "WHERE m.chatRoom.id = :chatRoomId " +
           "AND m.id < :beforeId " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessage> findMessagesBefore(
            @Param("chatRoomId") Long chatRoomId,
            @Param("beforeId") Long beforeId,
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
