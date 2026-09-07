package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import com.silverithm.vehicleplacementsystem.repository.ChatMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 축소본이 없는 옛 사진 메시지에 축소본을 만들어 채운다.
 *
 * <p>축소본은 2026-09-02부터 올라오는 사진에만 붙었다. 그 전 사진은 화면이 <b>원본을 그대로</b>
 * 그린다 — 하루치가 평균 2.4MB짜리 98장인 방도 있다. 옛 대화를 훑으면 브라우저가 수백 MB를
 * 한꺼번에 디코딩하고, 그때 사진이 깨져 보인다는 제보가 나왔다("종종 사진 깨짐").
 *
 * <p>저장된 파일 자체는 멀쩡하다(원본·축소본 770개의 종료 마커를 검사해 확인했다). 그래서
 * 파일을 고치는 것이 아니라, <b>목록이 무거운 원본을 그리지 않게</b> 축소본을 채워 넣는다.
 *
 * <p>한 번에 다 돌리지 않고 limit만큼 끊어서 돌린다 — 오래 잡고 있으면 다른 일이 밀린다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatThumbnailBackfillService {

    private final ChatMessageRepository chatMessageRepository;
    private final FileStorageService fileStorageService;

    /**
     * @param filled  새로 축소본을 붙인 메시지 수
     * @param skipped 축소본이 필요 없거나 만들 수 없는 것 (이미 작은 사진, ImageIO가 못 읽는 HEIC·WEBP)
     * @param failed  내려받기·저장이 실패한 것 — 다음 실행에서 다시 시도된다
     */
    public record Result(int filled, int skipped, int failed) {
        public int total() {
            return filled + skipped + failed;
        }
    }

    /** 한 번에 처리할 수 있는 최대 건수 — 로그인만 하면 부를 수 있는 길이라 상한을 둔다 */
    private static final int MAX_LIMIT = 500;

    @Transactional
    public Result backfill(int limit) {
        int bounded = Math.min(Math.max(1, limit), MAX_LIMIT);
        List<ChatMessage> targets = chatMessageRepository.findImagesMissingThumbnail(
                ChatMessage.MessageType.IMAGE, PageRequest.of(0, bounded));

        log.info("[Thumbnail Backfill] 대상 {}건 (limit={})", targets.size(), bounded);

        int filled = 0;
        int skipped = 0;
        int failed = 0;

        for (ChatMessage message : targets) {
            try {
                String path = toRelativePath(message.getFileUrl());
                byte[] content = fileStorageService.loadFile(path);
                String thumbnailPath = fileStorageService.generateAndStoreThumbnail(content, path);

                if (thumbnailPath == null) {
                    // 이미 충분히 작거나 자바가 못 읽는 포맷 — 억지로 만들지 않는다
                    skipped++;
                    continue;
                }

                message.setThumbnailUrl(fileStorageService.getFileUrl(thumbnailPath));
                filled++;
            } catch (Exception e) {
                failed++;
                log.warn("[Thumbnail Backfill] 실패: messageId={}, error={}", message.getId(), e.getMessage());
            }
        }

        log.info("[Thumbnail Backfill] 완료 - 채움 {}, 건너뜀 {}, 실패 {}", filled, skipped, failed);
        return new Result(filled, skipped, failed);
    }

    /**
     * 저장된 절대 S3 URL을 버킷 안 상대 경로로 되돌린다.
     * 우리 버킷이 아닌 주소는 그대로 둔다 — loadFile이 거기서 실패하고 failed로 잡힌다.
     */
    private String toRelativePath(String fileUrl) {
        String prefix = fileStorageService.getFileUrl("");
        if (prefix != null && !prefix.isBlank() && fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }
        return fileUrl;
    }
}
