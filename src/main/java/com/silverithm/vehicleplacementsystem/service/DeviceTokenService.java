package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.UserDevice;
import com.silverithm.vehicleplacementsystem.repository.UserDeviceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림을 받을 기기 토큰을 관리한다.
 *
 * <p>사용자 행의 fcm_token 컬럼은 그대로 두고 "마지막에 쓴 기기"로만 남긴다. 실제 발송 대상은
 * user_devices이며, 컬럼에만 남아 있고 아직 기기 행이 없는 경우까지 합쳐서 돌려주므로
 * 옮기는 도중에도 알림이 끊기지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenService {

    private final UserDeviceRepository userDeviceRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 기기를 등록하거나 마지막 사용 시각을 갱신한다.
     *
     * <p>같은 토큰이 다른 계정에 매여 있으면 주인을 갈아끼운다 — 한 기기를 여러 사람이 번갈아
     * 쓰는 경우, 지금 로그인한 사람에게만 알림이 가야 한다.
     */
    public void register(Long memberId, Long appUserId, String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }

        // 등록은 항상 새 트랜잭션에서 시도한다 — 같은 토큰이 거의 동시에 두 번 등록되는
        // 레이스(앱이 로그인 직후 여러 경로로 토큰을 올린다)에서 중복 키가 나면 이 작은
        // 트랜잭션만 버리고, 호출자 트랜잭션(FCM 토큰 갱신 등)은 오염시키지 않는다.
        // 주의: @Transactional(REQUIRES_NEW) + 메서드 안 catch로 바꾸면 안 된다 —
        // 리포지토리 프록시가 그 트랜잭션을 rollback-only로 만들어 커밋 시점에
        // UnexpectedRollbackException이 터진다. catch는 반드시 트랜잭션 경계 밖이어야 한다.
        TransactionTemplate registerTx = new TransactionTemplate(transactionManager);
        registerTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            registerTx.executeWithoutResult(status -> {
                Optional<UserDevice> existing = userDeviceRepository.findByFcmToken(fcmToken);
                if (existing.isPresent()) {
                    UserDevice device = existing.get();
                    device.reassignTo(memberId, appUserId);
                    userDeviceRepository.save(device);
                    return;
                }

                userDeviceRepository.saveAndFlush(UserDevice.builder()
                        .memberId(memberId)
                        .appUserId(appUserId)
                        .fcmToken(fcmToken)
                        .createdAt(LocalDateTime.now())
                        .lastSeenAt(LocalDateTime.now())
                        .build());
                log.info("[Device Token] 기기 등록: memberId={}, appUserId={}", memberId, appUserId);
            });
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 먼저 들어간 행이 이기게 두고 조용히 넘어간다.
            // 계정이 달랐다면 다음 등록 호출(토큰 갱신·재로그인)에서 reassign된다.
            log.info("[Device Token] 동시 등록 레이스 흡수: memberId={}, appUserId={}", memberId, appUserId);
        }
    }

    /** 이 토큰이 어느 계정의 기기인지. 아직 옮겨지지 않은 토큰이면 비어 있다. */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<UserDevice> ownerOf(String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return Optional.empty();
        }
        return userDeviceRepository.findByFcmToken(fcmToken);
    }

    /** 로그아웃·토큰 무효화 시 그 기기만 뗀다 (다른 기기는 계속 받는다) */
    @Transactional
    public void remove(String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }
        userDeviceRepository.deleteByFcmToken(fcmToken);
    }

    /** 직원의 모든 기기 토큰. legacyToken은 아직 옮겨지지 않은 컬럼 값. */
    @Transactional(readOnly = true)
    public List<String> tokensOfMember(Long memberId, String legacyToken) {
        return merge(userDeviceRepository.findByMemberId(memberId), legacyToken);
    }

    /** 관리자 가입 계정의 모든 기기 토큰. */
    @Transactional(readOnly = true)
    public List<String> tokensOfAppUser(Long appUserId, String legacyToken) {
        return merge(userDeviceRepository.findByAppUserId(appUserId), legacyToken);
    }

    /**
     * 이 토큰을 가진 사람의 다른 기기 토큰까지 모두. 주인을 못 찾으면 받은 토큰만 돌려준다.
     *
     * <p>발송 지점이 여러 곳이라 각자 기기 목록을 조회하게 두면 빠뜨린다. 토큰 하나로 호출해도
     * 그 사람의 모든 기기로 나가도록 FCMService가 이 메서드를 거친다.
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<String> siblingTokensOf(String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return List.of();
        }
        try {
            Optional<UserDevice> device = userDeviceRepository.findByFcmToken(fcmToken);
            if (device.isEmpty()) {
                return List.of(fcmToken);
            }

            UserDevice owner = device.get();
            List<UserDevice> siblings = owner.getMemberId() != null
                    ? userDeviceRepository.findByMemberId(owner.getMemberId())
                    : owner.getAppUserId() != null
                            ? userDeviceRepository.findByAppUserId(owner.getAppUserId())
                            : List.of(owner);

            return merge(siblings, fcmToken);
        } catch (Exception e) {
            // 기기 조회가 실패해도 원래 토큰으로는 보낸다 — 알림을 통째로 잃는 것보다 낫다
            log.error("[Device Token] 기기 목록 조회 실패 — 단일 토큰으로 발송: {}", e.getMessage());
            return List.of(fcmToken);
        }
    }

    /** 기기 목록과 legacy 토큰을 순서 유지하며 합친다 */
    private List<String> merge(List<UserDevice> devices, String legacyToken) {
        Set<String> tokens = new LinkedHashSet<>();
        for (UserDevice device : devices) {
            if (device.getFcmToken() != null && !device.getFcmToken().isBlank()) {
                tokens.add(device.getFcmToken());
            }
        }
        if (legacyToken != null && !legacyToken.isBlank()) {
            tokens.add(legacyToken);
        }
        return new ArrayList<>(tokens);
    }
}
