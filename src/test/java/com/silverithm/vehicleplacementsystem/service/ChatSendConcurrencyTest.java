package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.config.ThreadConfig.ChatNotificationExecutor;
import com.silverithm.vehicleplacementsystem.config.querydsl.QuerydslConfiguration;
import com.silverithm.vehicleplacementsystem.dto.ChatMessageCreateRequest;
import com.silverithm.vehicleplacementsystem.dto.ChatMessageDTO;
import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 방에 여러 메시지가 **동시에** 와도 교착이 없다.
 *
 * <p>운영 2026-09-14 11:27, 한 사람이 사진 세 장을 한 번에 올렸는데 한 장이
 * "Deadlock found when trying to get lock"으로 500이 났다. 원인은 잠금 순서다 — 메시지 INSERT가
 * 외래키 검사로 방 행의 공유 잠금을 먼저 잡고, 그 뒤 방 UPDATE가 배타 잠금을 요구한다. 셋이 함께
 * 공유 잠금을 쥔 채 서로의 배타 잠금을 기다리면 교착이다. 고친 순서는 배타 잠금부터 잡는다.
 *
 * <p>H2는 이 잠금 모델이 아니라 교착을 재현하지 못한다. 그래서 진짜 MySQL을 가리키는 환경변수가
 * 있으면 그쪽으로 돈다 — {@code CHAT_TEST_DB_URL} 등. 예전 순서가 실제로 교착하는지도 MySQL에서만 본다.
 */
@DataJpaTest
@Import({QuerydslConfiguration.class, BillingKeyEncryptionConfig.class, ChatMessageWriter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "billing.encryption.key=dGVzdC1vbmx5LWtleS1mb3ItamVwYS1zbGljZS10ZXN0cw==",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=${CHAT_TEST_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.datasource.url=${CHAT_TEST_DB_URL:jdbc:h2:mem:chatconc;MODE=MySQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${CHAT_TEST_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${CHAT_TEST_USER:sa}",
        "spring.datasource.password=${CHAT_TEST_PASS:}",
        "spring.datasource.hikari.maximum-pool-size=25",
        "logging.level.org.hibernate.SQL=WARN"
})
class ChatSendConcurrencyTest {

    private static final int SENDERS = 5;
    private static final int THREADS = 20;

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatMessageReadRepository chatMessageReadRepository;
    @Autowired private ChatMessageReactionRepository chatMessageReactionRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ChatMessageWriter writer;
    @Value("${spring.datasource.url}") private String jdbcUrl;

    private SimpleMeterRegistry registry;
    private ChatService chatService;
    private TransactionTemplate tx;
    private Long roomId;

    private static ChatNotificationExecutor directExecutor() {
        ChatNotificationExecutor executor = new ChatNotificationExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    private boolean onMySql() {
        return jdbcUrl.startsWith("jdbc:mysql:");
    }

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        chatService = new ChatService(
                chatRoomRepository, chatParticipantRepository, chatMessageRepository,
                chatMessageReadRepository, chatMessageReactionRepository, companyRepository,
                memberRepository, userRepository,
                mock(SimpMessagingTemplate.class), mock(NotificationService.class),
                mock(ResourceScopeGuard.class), directExecutor(),
                new ChatReadRecorder(chatMessageReadRepository, txManager),
                writer, ChatTestSupport.metrics(registry));

        tx = new TransactionTemplate(txManager);
        roomId = tx.execute(status -> {
            chatMessageReadRepository.deleteAll();
            chatMessageRepository.deleteAll();
            chatParticipantRepository.deleteAll();
            chatRoomRepository.deleteAll();
            companyRepository.deleteAll();

            Company company = companyRepository.save(Company.of("햇살요양원", "서울", null));
            ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                    .name("방").company(company).createdBy("1").createdByName("사람1")
                    .status(ChatRoom.ChatRoomStatus.ACTIVE).build());
            for (int i = 1; i <= SENDERS; i++) {
                chatParticipantRepository.save(ChatParticipant.builder()
                        .chatRoom(room).userId(String.valueOf(i)).userName("사람" + i).isActive(true).build());
            }
            return room.getId();
        });
    }

    /** 스레드 THREADS개가 동시에 출발해 각자 한 번씩 일한다. 던진 예외를 모아 돌려준다. */
    private List<Throwable> runConcurrently(IntFunction<Runnable> job) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < THREADS; i++) {
            Runnable work = job.apply(i);
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await();
                    work.run();
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        go.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).as("60초 안에 끝나야 한다").isTrue();
        pool.shutdownNow();
        return errors;
    }

    private ChatMessageCreateRequest photo(int sender, String clientMessageId) {
        return ChatMessageCreateRequest.builder()
                .senderId(String.valueOf(sender)).senderName("사람" + sender)
                .type("IMAGE").content("사진.jpg").fileUrl("https://x/" + clientMessageId + ".jpg")
                .clientMessageId(clientMessageId).build();
    }

    @Test
    @DisplayName("같은 방에 동시에 20건 — 모두 저장되고 교착으로 되돌아간 것이 하나도 없다")
    void concurrentSendsToSameRoomDoNotDeadlock() throws InterruptedException {
        List<Throwable> errors = runConcurrently(i ->
                () -> chatService.sendMessage(roomId, photo(1 + i % SENDERS, UUID.randomUUID().toString())));

        assertThat(errors).as("전송 중 예외").isEmpty();
        assertThat(chatMessageRepository.count()).isEqualTo(THREADS);
        // 재시도로 버틴 게 아니라 잠금 순서로 애초에 교착이 없어야 한다
        assertThat(ChatTestSupport.count(registry, "deadlock_retry")).isZero();
        assertThat(ChatTestSupport.count(registry, "stored")).isEqualTo(THREADS);
    }

    @Test
    @DisplayName("같은 식별자로 동시에 10번 보내도 한 건만 남고 모두 같은 메시지를 돌려받는다")
    void sameKeyConcurrentlyStoresOnce() throws InterruptedException {
        String key = UUID.randomUUID().toString();
        List<Long> ids = Collections.synchronizedList(new ArrayList<>());

        List<Throwable> errors = runConcurrently(i -> () -> {
            ChatMessageDTO dto = chatService.sendMessage(roomId, photo(1, key));
            ids.add(dto.getId());
        });

        assertThat(errors).isEmpty();
        assertThat(chatMessageRepository.count()).isEqualTo(1);
        assertThat(ids).hasSize(THREADS);
        assertThat(ids.stream().distinct().count()).isEqualTo(1);
        assertThat(ChatTestSupport.count(registry, "stored")).isEqualTo(1);
        assertThat(ChatTestSupport.count(registry, "duplicate")).isEqualTo(THREADS - 1);
    }

    /**
     * 진단이 맞는지 — **예전 순서**(INSERT 뒤 방 UPDATE)를 그대로 흉내 내면 MySQL에서 실제로 교착한다.
     * 이 테스트가 통과하지 않으면 위 테스트의 통과는 우연일 수 있다.
     */
    @Test
    @DisplayName("[MySQL] 예전 순서(메시지 INSERT → 방 UPDATE)는 실제로 교착한다 — 진단의 증거")
    void legacyOrderDeadlocksOnMySql() throws InterruptedException {
        Assumptions.assumeTrue(onMySql(), "H2는 InnoDB 잠금 모델이 아니라 재현되지 않는다");

        List<Throwable> errors = runConcurrently(i -> () -> tx.executeWithoutResult(status -> {
            ChatRoom room = chatRoomRepository.getReferenceById(roomId);
            chatMessageRepository.saveAndFlush(ChatMessage.builder()
                    .chatRoom(room).senderId("1").senderName("사람1")
                    .type(ChatMessage.MessageType.IMAGE).content("사진.jpg").isDeleted(false).build());
            // 외래키 검사로 방 행의 공유 잠금을 쥔 채 잠깐 머문다 — 다른 스레드도 같은 자리에 오게
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            em.createQuery("UPDATE ChatRoom r SET r.lastMessageAt = :at WHERE r.id = :id")
                    .setParameter("at", LocalDateTime.now()).setParameter("id", roomId).executeUpdate();
        }));

        assertThat(errors).as("예전 순서는 최소 한 건은 교착으로 되돌아가야 한다")
                .isNotEmpty()
                .allMatch(ChatSendConcurrencyTest::isDeadlock);
        assertThat(chatMessageRepository.count()).as("교착으로 되돌아간 만큼 메시지가 사라진다")
                .isLessThan(THREADS);
    }

    /**
     * 같은 원시 순서를 **배타 잠금부터** 잡도록만 바꾸면 교착이 사라진다 — 고친 순서의 근거.
     */
    @Test
    @DisplayName("[MySQL] 방 UPDATE를 먼저 하는 새 순서는 같은 조건에서 교착하지 않는다")
    void lockFirstOrderDoesNotDeadlockOnMySql() throws InterruptedException {
        Assumptions.assumeTrue(onMySql(), "H2는 InnoDB 잠금 모델이 아니라 재현되지 않는다");

        List<Throwable> errors = runConcurrently(i -> () -> tx.executeWithoutResult(status -> {
            em.createQuery("UPDATE ChatRoom r SET r.lastMessageAt = :at WHERE r.id = :id")
                    .setParameter("at", LocalDateTime.now()).setParameter("id", roomId).executeUpdate();
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            chatMessageRepository.saveAndFlush(ChatMessage.builder()
                    .chatRoom(chatRoomRepository.getReferenceById(roomId)).senderId("1").senderName("사람1")
                    .type(ChatMessage.MessageType.IMAGE).content("사진.jpg").isDeleted(false).build());
        }));

        assertThat(errors).isEmpty();
        assertThat(chatMessageRepository.count()).isEqualTo(THREADS);
    }

    /** 교착은 스프링 번역(PessimisticLockingFailureException)으로도, JPA 예외로 감싸여서도 온다. */
    private static boolean isDeadlock(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof PessimisticLockingFailureException) return true;
            if (c.getMessage() != null && c.getMessage().contains("Deadlock found")) return true;
        }
        return false;
    }
}
