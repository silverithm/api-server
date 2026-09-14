package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.dto.ChatMessageCreateRequest;
import com.silverithm.vehicleplacementsystem.dto.ChatMessageDTO;
import com.silverithm.vehicleplacementsystem.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 저장이 실패했을 때 {@link ChatService#sendMessage}가 어떻게 버티는지 — DB 없이 저장기를 흉내 내어 본다.
 *
 * 운영에서 사진 세 장을 동시에 보내다 한 장이 교착(Deadlock)으로 500이 났다. 교착은 DB가
 * 한쪽을 골라 되돌리는 정상 동작이라 다시 시도하면 되는데, 그 자리가 없어 그대로 실패로 나갔다.
 */
class ChatServiceSendRetryTest {

    private ChatMessageWriter writer;
    private SimpMessagingTemplate messagingTemplate;
    private SimpleMeterRegistry registry;
    private ChatService chatService;

    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    private static ChatMessageCreateRequest request(String clientMessageId) {
        return ChatMessageCreateRequest.builder()
                .senderId("7").senderName("보낸이").type("TEXT").content("안녕")
                .clientMessageId(clientMessageId).build();
    }

    private static ChatMessageWriter.Stored stored(long id, String clientMessageId, boolean duplicate) {
        return new ChatMessageWriter.Stored(
                ChatMessageDTO.builder().id(id).chatRoomId(1L).clientMessageId(clientMessageId).build(), duplicate);
    }

    @BeforeEach
    void setUp() {
        writer = mock(ChatMessageWriter.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        registry = new SimpleMeterRegistry();
        chatService = new ChatService(
                mock(ChatRoomRepository.class), mock(ChatParticipantRepository.class),
                mock(ChatMessageRepository.class), mock(ChatMessageReadRepository.class),
                mock(ChatMessageReactionRepository.class), mock(CompanyRepository.class),
                mock(MemberRepository.class), mock(UserRepository.class),
                messagingTemplate, mock(NotificationService.class),
                mock(ResourceScopeGuard.class), directExecutor(),
                mock(ChatReadRecorder.class), writer, ChatTestSupport.metrics(registry));
    }

    @Test
    @DisplayName("교착으로 되돌아가면 새로 다시 시도해 결국 저장한다 — 사진 세 장 중 한 장이 사라지던 자리")
    void retriesAfterDeadlock() {
        when(writer.persist(eq(1L), any(), any()))
                .thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
                .thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
                .thenReturn(stored(10L, "k1", false));

        ChatMessageDTO dto = chatService.sendMessage(1L, request("k1"));

        assertThat(dto.getId()).isEqualTo(10L);
        verify(writer, times(3)).persist(eq(1L), any(), any());
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/chat/1"), any(Object.class));
        assertThat(ChatTestSupport.count(registry, "deadlock_retry")).isEqualTo(2);
        assertThat(ChatTestSupport.count(registry, "stored")).isEqualTo(1);
    }

    @Test
    @DisplayName("세 번 다 교착이면 포기하고 실패를 알린다 — 영원히 매달리지 않는다")
    void givesUpAfterThreeDeadlocks() {
        when(writer.persist(eq(1L), any(), any()))
                .thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"));

        assertThatThrownBy(() -> chatService.sendMessage(1L, request("k1")))
                .isInstanceOf(CannotAcquireLockException.class);

        verify(writer, times(ChatService.SEND_ATTEMPTS)).persist(eq(1L), any(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        assertThat(ChatTestSupport.count(registry, "failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 식별자가 한발 먼저 들어가 유니크에 막히면 그 메시지를 돌려주고 방송은 다시 하지 않는다")
    void duplicateKeyRaceReturnsExistingWithoutRebroadcast() {
        when(writer.persist(eq(1L), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_chat_message_client_id"));
        when(writer.findExistingDto(1L, "7", "k1"))
                .thenReturn(Optional.of(stored(42L, "k1", true).dto()));

        ChatMessageDTO dto = chatService.sendMessage(1L, request("k1"));

        assertThat(dto.getId()).isEqualTo(42L);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        assertThat(ChatTestSupport.count(registry, "duplicate")).isEqualTo(1);
    }

    @Test
    @DisplayName("식별자 없이 온 요청이 유니크에 막히면 그건 다른 문제다 — 숨기지 않고 그대로 올린다")
    void integrityErrorWithoutKeyIsNotSwallowed() {
        when(writer.persist(eq(1L), any(), any()))
                .thenThrow(new DataIntegrityViolationException("some other constraint"));
        when(writer.findExistingDto(anyLong(), anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(1L, request(null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(ChatTestSupport.count(registry, "failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("저장기가 '이미 있던 메시지'라고 하면 방송도 알림도 다시 나가지 않는다")
    void duplicateFromWriterIsQuiet() {
        when(writer.persist(eq(1L), any(), any())).thenReturn(stored(5L, "k1", true));

        ChatMessageDTO dto = chatService.sendMessage(1L, request("k1"));

        assertThat(dto.getId()).isEqualTo(5L);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        assertThat(ChatTestSupport.count(registry, "duplicate")).isEqualTo(1);
        assertThat(ChatTestSupport.count(registry, "stored")).isEqualTo(0);
    }
}
