package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message_reactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(nullable = false)
    private String userId;

    /**
     * 사람을 가리키는 제대로 된 참조 (V1.66). 문자열 userId와 함께 채워둔다 —
     * 조회는 아직 문자열을 쓰고, 이 칼럼들이 FK로 무결성을 지킨다. 규칙은 {@link ChatPersonRef}.
     */
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "app_user_id")
    private Long appUserId;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false, length = 50)
    private String emoji;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // 문자열 식별자와 참조 칼럼이 어긋나지 않게 함께 채운다 (V1.66, ChatPersonRef 참고)
        syncPersonRef();
    }

    /** 문자열 userId에서 참조 칼럼을 파생시킨다. 어느 경로로 저장되든 둘이 같은 사람을 가리키게 한다. */
    @PreUpdate
    void syncPersonRef() {
        ChatPersonRef ref = ChatPersonRef.of(this.userId);
        this.memberId = ref.memberId();
        this.appUserId = ref.appUserId();
    }
}
