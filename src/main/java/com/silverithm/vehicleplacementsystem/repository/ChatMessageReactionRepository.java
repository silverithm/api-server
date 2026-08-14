package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ChatMessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageReactionRepository extends JpaRepository<ChatMessageReaction, Long> {

    List<ChatMessageReaction> findByMessageId(Long messageId);

    List<ChatMessageReaction> findByMessageIdIn(List<Long> messageIds);

    @Query("SELECT r FROM ChatMessageReaction r WHERE r.message.id = :messageId AND r.emoji = :emoji "
            + "AND ((:memberId IS NOT NULL AND r.memberId = :memberId) "
            + "OR (:appUserId IS NOT NULL AND r.appUserId = :appUserId))")
    Optional<ChatMessageReaction> findByMessageAndPersonAndEmoji(@Param("messageId") Long messageId,
                                                                 @Param("memberId") Long memberId,
                                                                 @Param("appUserId") Long appUserId,
                                                                 @Param("emoji") String emoji);

    void deleteByMessageIdAndUserIdAndEmoji(Long messageId, String userId, String emoji);

    @Query("SELECT r.emoji, COUNT(r) FROM ChatMessageReaction r WHERE r.message.id = :messageId GROUP BY r.emoji")
    List<Object[]> countByMessageIdGroupByEmoji(@Param("messageId") Long messageId);

    boolean existsByMessageIdAndUserIdAndEmoji(Long messageId, String userId, String emoji);
}
