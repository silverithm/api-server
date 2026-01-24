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

    Optional<ChatMessageReaction> findByMessageIdAndUserIdAndEmoji(Long messageId, String userId, String emoji);

    void deleteByMessageIdAndUserIdAndEmoji(Long messageId, String userId, String emoji);

    @Query("SELECT r.emoji, COUNT(r) FROM ChatMessageReaction r WHERE r.message.id = :messageId GROUP BY r.emoji")
    List<Object[]> countByMessageIdGroupByEmoji(@Param("messageId") Long messageId);

    boolean existsByMessageIdAndUserIdAndEmoji(Long messageId, String userId, String emoji);
}
