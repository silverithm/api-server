package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatPersonRef;
import lombok.*;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {

    private Long id;
    private Long chatRoomId;
    /**
     * 예전부터 쓰던 문자열 표기("12" / "admin_12").
     * 클라이언트가 아직 이 값으로 '내 메시지인지'를 판단해서 계속 내려준다.
     * 새로 만드는 화면은 아래 senderType/senderRefId를 쓸 것 — 이 필드는 앱이 옮겨가면 뺀다.
     */
    @Deprecated
    private String senderId;
    /** ADMIN | MEMBER — 결재선의 approverType과 같은 표기 */
    private String senderType;
    /** 보낸 사람의 원시 id (app_user.id 또는 members.id) */
    private Long senderRefId;
    private String senderName;
    private String senderPosition;
    private String type;
    private String content;
    private LocalDateTime createdAt;
    private Boolean isDeleted;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String thumbnailUrl;
    /**
     * 화면에서 어떻게 그릴지 알려주는 파생 값 — IMAGE | VIDEO | FILE (첨부가 아니면 null).
     * 저장된 {@link #type}은 건드리지 않는다. 이유는 {@link ChatMediaType} 주석 참고.
     */
    private String mediaType;
    private int readCount;
    private String displayContent;

    @Builder.Default
    private List<ChatReactionDTO.ReactionSummary> reactions = new ArrayList<>();

    // 답글 관련 필드
    private Long replyToId;
    private String replyToSenderName;
    private String replyToContent;
    private String replyToType;
    /** 답글 미리보기에서도 동영상을 알아볼 수 있게 — IMAGE | VIDEO | FILE. */
    private String replyToMediaType;

    public static ChatMessageDTO fromEntity(ChatMessage message) {
        // 문자열 표기는 이제 저장된 칼럼이 아니라 참조에서 만든다.
        // (짝이 맞지 않는 옛 행은 참조가 비어 있어 저장된 문자열을 그대로 쓴다)
        ChatPersonRef person = ChatPersonRef.of(message.getSenderMemberId(), message.getSenderAppUserId());

        ChatMessageDTO.ChatMessageDTOBuilder builder = ChatMessageDTO.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoom() != null ? message.getChatRoom().getId() : null)
                .senderId(person.legacyId() != null ? person.legacyId() : message.getSenderId())
                .senderType(person.type())
                .senderRefId(person.refId())
                .senderName(message.getSenderName())
                .senderPosition(message.getSenderPosition())
                .type(message.getType().name())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .isDeleted(message.getIsDeleted())
                .fileUrl(message.getFileUrl())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .mimeType(message.getMimeType())
                .thumbnailUrl(message.getThumbnailUrl())
                .mediaType(ChatMediaType.resolve(message.getType().name(),
                        message.getMimeType(), message.getFileName()))
                // 읽은 사람 수는 목록 조회에서 한 번에 세어 덮어쓴다(fromEntityWithReadCount).
                // 여기서 컬렉션을 건드리면 메시지 한 건마다 SELECT가 하나씩 더 나가므로
                // (30건 조회에 30번) 아직 로딩되지 않은 컬렉션은 만지지 않는다.
                .readCount(message.getReaders() != null && Hibernate.isInitialized(message.getReaders())
                        ? message.getReaders().size() : 0)
                .displayContent(message.getDisplayContent());

        // 답글 정보 포함
        if (message.getReplyTo() != null) {
            ChatMessage replyTo = message.getReplyTo();
            builder.replyToId(replyTo.getId())
                    .replyToSenderName(replyTo.getSenderName())
                    .replyToContent(replyTo.getIsDeleted() ? "삭제된 메시지입니다" : replyTo.getDisplayContent())
                    .replyToType(replyTo.getType().name())
                    .replyToMediaType(ChatMediaType.resolve(replyTo.getType().name(),
                            replyTo.getMimeType(), replyTo.getFileName()));
        }

        return builder.build();
    }

    public static ChatMessageDTO fromEntityWithReadCount(ChatMessage message, int readCount) {
        ChatMessageDTO dto = fromEntity(message);
        dto.setReadCount(readCount);
        return dto;
    }
}
