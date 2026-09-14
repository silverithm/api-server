package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.ChatMessageRead;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageReadRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 읽음 기록을 남긴다. **이미 남아 있으면 조용히 넘어간다.**
 *
 * <p>같은 사람이 앱과 웹을 함께 켜 두면 두 경로가 거의 동시에 읽음을 보낸다. 둘 다 '안 읽음'으로
 * 조회한 뒤 둘 다 저장을 시도하는데, {@code (message_id, user_id)} 유니크 제약이 뒤엣것을 막아
 * <b>읽음 처리가 500으로 떨어졌다</b> — 운영에서 하루 15건이 그렇게 났다.
 *
 * <p><b>왜 별도 트랜잭션인가.</b> 부르는 쪽 트랜잭션 안에서 제약을 어기면 그 트랜잭션이 통째로
 * 되돌아간다. 읽음 위치 갱신처럼 이미 끝난 일까지 함께 사라지고, 예외는 커밋 시점에 터져
 * 서비스 메서드 안에서 잡을 수도 없다. 그래서 이 저장만 떼어내 자기 트랜잭션에서 처리한다.
 *
 * <p><b>왜 애노테이션이 아니라 TransactionTemplate인가.</b> 처음엔 {@code @Transactional(REQUIRES_NEW)}
 * 안에서 예외를 잡고 false를 돌려줬다. 그런데 JPA는 제약 위반이 나는 순간 그 트랜잭션에
 * rollback-only 표시를 남기고, 메서드가 정상 반환하면 커밋하려다
 * "Transaction silently rolled back because it has been marked as rollback-only"로 다시 터졌다
 * (운영 2026-09-14, 읽음 처리 500). 템플릿은 예외가 나면 그 트랜잭션을 되돌리고 끝내므로
 * 커밋을 시도하지 않는다. 프록시 없이 {@code new}로 만들어도 같은 동작이라 검사할 수 있다.
 *
 * <p>실패한 엔티티는 별도 트랜잭션의 영속성 컨텍스트와 함께 버려진다. 여기서 EntityManager를
 * clear하면 안 된다 — 그건 부르는 쪽의 컨텍스트라, 아직 flush되지 않은 읽음 위치 갱신까지 지운다.
 *
 * <p>DB 전용 구문(INSERT IGNORE 등)을 쓰지 않는 이유는 테스트 DB(H2)가 그 문법을 모르기 때문이다.
 */
@Service
@Slf4j
public class ChatReadRecorder {

    private final ChatMessageReadRepository chatMessageReadRepository;
    private final TransactionTemplate ownTransaction;

    public ChatReadRecorder(ChatMessageReadRepository chatMessageReadRepository,
                            PlatformTransactionManager transactionManager) {
        this.chatMessageReadRepository = chatMessageReadRepository;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 여러 건을 한 번에 남긴다. 하나라도 겹치면 실패하므로, 그때는 호출자가 한 건씩 다시 부른다.
     *
     * @return 모두 남겼으면 true, 겹친 것이 있어 실패했으면 false
     */
    public boolean recordAll(List<ChatMessageRead> reads) {
        try {
            ownTransaction.executeWithoutResult(status -> {
                chatMessageReadRepository.saveAll(reads);
                // 여기서 밀어 넣어야 예외가 이 트랜잭션 안에서 잡힌다
                chatMessageReadRepository.flush();
            });
            return true;
        } catch (DataIntegrityViolationException | UnexpectedRollbackException e) {
            return false;
        }
    }

    /** 한 건을 남긴다. 이미 있으면 아무 일도 하지 않는다. */
    public void recordOne(ChatMessageRead read) {
        try {
            ownTransaction.executeWithoutResult(status -> {
                chatMessageReadRepository.save(read);
                chatMessageReadRepository.flush();
            });
        } catch (DataIntegrityViolationException | UnexpectedRollbackException e) {
            // 이미 읽었다고 적혀 있다 — 읽음은 멱등하므로 그걸로 된 것이다
            log.debug("[Chat Read] 이미 읽음 기록이 있어 건너뜀: userId={}", read.getUserId());
        }
    }
}
