package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.CreateNoticeRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.FCMNotificationRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.NoticeCommentDTO;
import com.silverithm.vehicleplacementsystem.dto.NoticeDTO;
import com.silverithm.vehicleplacementsystem.dto.NoticeReaderDTO;
import com.silverithm.vehicleplacementsystem.dto.UpdateNoticeRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Notice;
import com.silverithm.vehicleplacementsystem.entity.NoticeComment;
import com.silverithm.vehicleplacementsystem.entity.NoticeReader;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.NoticeCommentRepository;
import com.silverithm.vehicleplacementsystem.repository.NoticeReaderRepository;
import com.silverithm.vehicleplacementsystem.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final NoticeReaderRepository noticeReaderRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    /**
     * 공지사항 생성
     */
    @Transactional
    public NoticeDTO createNotice(Long companyId, String authorId, String authorName,
                                   CreateNoticeRequestDTO request) {
        log.info("[Notice Service] 공지사항 생성: companyId={}, author={}", companyId, authorName);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        Notice notice = Notice.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .priority(parsePriority(request.getPriority()))
                .status(Notice.NoticeStatus.PUBLISHED) // 즉시 게시
                .isPinned(request.getIsPinned())
                .authorId(authorId)
                .authorName(authorName)
                .company(company)
                .viewCount(0)
                .publishedAt(LocalDateTime.now())
                .build();

        Notice saved = noticeRepository.save(notice);
        log.info("[Notice Service] 공지사항 저장 완료: id={}", saved.getId());

        // FCM 알림 전송 (요청 시)
        if (Boolean.TRUE.equals(request.getSendPushNotification())) {
            sendNoticeNotificationToAllMembers(company, saved);
        }

        return NoticeDTO.fromEntity(saved);
    }

    /**
     * 공지사항 수정
     */
    @Transactional
    public NoticeDTO updateNotice(Long noticeId, UpdateNoticeRequestDTO request) {
        log.info("[Notice Service] 공지사항 수정: id={}", noticeId);

        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));

        if (request.getTitle() != null) {
            notice.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            notice.setContent(request.getContent());
        }
        if (request.getPriority() != null) {
            notice.setPriority(parsePriority(request.getPriority()));
        }
        if (request.getIsPinned() != null) {
            notice.setIsPinned(request.getIsPinned());
        }
        if (request.getStatus() != null) {
            Notice.NoticeStatus newStatus = parseStatus(request.getStatus());
            if (newStatus == Notice.NoticeStatus.PUBLISHED &&
                notice.getStatus() != Notice.NoticeStatus.PUBLISHED) {
                notice.publish();
            } else {
                notice.setStatus(newStatus);
            }
        }

        Notice saved = noticeRepository.save(notice);
        log.info("[Notice Service] 공지사항 수정 완료: id={}", saved.getId());

        return NoticeDTO.fromEntity(saved);
    }

    /**
     * 공지사항 삭제
     */
    @Transactional
    public void deleteNotice(Long noticeId) {
        log.info("[Notice Service] 공지사항 삭제: id={}", noticeId);

        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));

        noticeRepository.delete(notice);
        log.info("[Notice Service] 공지사항 삭제 완료: id={}", noticeId);
    }

    /**
     * 공지사항 상세 조회
     */
    @Transactional(readOnly = true)
    public NoticeDTO getNotice(Long noticeId) {
        log.info("[Notice Service] 공지사항 조회: id={}", noticeId);

        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));

        return NoticeDTO.fromEntity(notice);
    }

    /**
     * 조회수 증가
     */
    @Transactional
    public NoticeDTO incrementViewCount(Long noticeId) {
        log.info("[Notice Service] 조회수 증가: id={}", noticeId);

        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));

        notice.incrementViewCount();
        Notice saved = noticeRepository.save(notice);

        return NoticeDTO.fromEntity(saved);
    }

    /**
     * 공지사항 목록 조회 (필터링)
     */
    @Transactional(readOnly = true)
    public List<NoticeDTO> getNotices(Long companyId, String status, String priority,
                                       String searchQuery, String startDate, String endDate) {
        log.info("[Notice Service] 공지사항 목록 조회: companyId={}, status={}, priority={}",
                companyId, status, priority);

        Notice.NoticeStatus noticeStatus = null;
        Notice.NoticePriority noticePriority = null;
        LocalDateTime start = null;
        LocalDateTime end = null;

        if (status != null && !status.equals("ALL")) {
            noticeStatus = parseStatus(status);
        }
        if (priority != null && !priority.equals("ALL")) {
            noticePriority = parsePriority(priority);
        }
        if (startDate != null && !startDate.isEmpty()) {
            start = LocalDateTime.parse(startDate + "T00:00:00");
        }
        if (endDate != null && !endDate.isEmpty()) {
            end = LocalDateTime.parse(endDate + "T23:59:59");
        }

        List<Notice> notices = noticeRepository.findByFilters(
                companyId, noticeStatus, noticePriority, searchQuery, start, end);

        return notices.stream()
                .map(NoticeDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 게시된 공지사항 조회 (직원용)
     */
    @Transactional(readOnly = true)
    public List<NoticeDTO> getPublishedNotices(Long companyId) {
        log.info("[Notice Service] 게시된 공지사항 조회: companyId={}", companyId);

        List<Notice> notices = noticeRepository.findByCompanyIdAndStatusOrderByIsPinnedDescPublishedAtDesc(
                companyId, Notice.NoticeStatus.PUBLISHED);

        return notices.stream()
                .map(NoticeDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 통계 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStats(Long companyId) {
        long total = noticeRepository.countByCompanyId(companyId);
        long published = noticeRepository.countByCompanyIdAndStatus(companyId, Notice.NoticeStatus.PUBLISHED);
        long draft = noticeRepository.countByCompanyIdAndStatus(companyId, Notice.NoticeStatus.DRAFT);
        long archived = noticeRepository.countByCompanyIdAndStatus(companyId, Notice.NoticeStatus.ARCHIVED);

        return Map.of(
                "total", total,
                "published", published,
                "draft", draft,
                "archived", archived
        );
    }

    // ==================== 댓글 관련 메서드 ====================

    /**
     * 댓글 목록 조회
     */
    @Transactional(readOnly = true)
    public List<NoticeCommentDTO> getComments(Long noticeId) {
        log.info("[Notice Service] 댓글 목록 조회: noticeId={}", noticeId);

        // 공지사항 존재 확인
        noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));

        List<NoticeComment> comments = noticeCommentRepository.findByNoticeIdOrderByCreatedAtAsc(noticeId);

        return comments.stream()
                .map(NoticeCommentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 댓글 생성
     */
    @Transactional
    public NoticeCommentDTO createComment(Long noticeId, String authorId, String authorName, String content) {
        log.info("[Notice Service] 댓글 생성: noticeId={}, author={}", noticeId, authorName);

        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));

        NoticeComment comment = NoticeComment.builder()
                .notice(notice)
                .authorId(authorId)
                .authorName(authorName)
                .content(content)
                .build();

        NoticeComment saved = noticeCommentRepository.save(comment);
        log.info("[Notice Service] 댓글 생성 완료: id={}", saved.getId());

        return NoticeCommentDTO.fromEntity(saved);
    }

    /**
     * 댓글 삭제
     */
    @Transactional
    public void deleteComment(Long noticeId, Long commentId) {
        log.info("[Notice Service] 댓글 삭제: noticeId={}, commentId={}", noticeId, commentId);

        // 공지사항 존재 확인
        noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));

        NoticeComment comment = noticeCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글을 찾을 수 없습니다: " + commentId));

        // 댓글이 해당 공지사항에 속하는지 확인
        if (!comment.getNotice().getId().equals(noticeId)) {
            throw new RuntimeException("해당 공지사항의 댓글이 아닙니다.");
        }

        noticeCommentRepository.delete(comment);
        log.info("[Notice Service] 댓글 삭제 완료: commentId={}", commentId);
    }

    // ==================== 읽음 확인 관련 메서드 ====================

    /**
     * 읽은 사용자 목록 조회
     */
    @Transactional(readOnly = true)
    public List<NoticeReaderDTO> getReaders(Long noticeId) {
        log.info("[Notice Service] 읽은 사용자 목록 조회: noticeId={}", noticeId);

        // 공지사항 존재 확인
        noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));

        List<NoticeReader> readers = noticeReaderRepository.findByNoticeIdOrderByReadAtDesc(noticeId);

        return readers.stream()
                .map(NoticeReaderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 읽음 표시
     */
    @Transactional
    public NoticeReaderDTO markAsRead(Long noticeId, String userId, String userName) {
        log.info("[Notice Service] 읽음 표시: noticeId={}, userId={}", noticeId, userId);

        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));

        // 이미 읽은 경우 기존 기록 반환
        return noticeReaderRepository.findByNoticeIdAndUserId(noticeId, userId)
                .map(NoticeReaderDTO::fromEntity)
                .orElseGet(() -> {
                    NoticeReader reader = NoticeReader.builder()
                            .notice(notice)
                            .userId(userId)
                            .userName(userName)
                            .readAt(LocalDateTime.now())
                            .build();

                    NoticeReader saved = noticeReaderRepository.save(reader);
                    log.info("[Notice Service] 읽음 표시 완료: id={}", saved.getId());

                    return NoticeReaderDTO.fromEntity(saved);
                });
    }

    /**
     * 전체 회원에게 공지사항 알림 전송
     */
    private void sendNoticeNotificationToAllMembers(Company company, Notice notice) {
        try {
            log.info("[Notice Service] 공지사항 FCM 알림 전송 시작: noticeId={}", notice.getId());

            // 해당 회사의 모든 활성 멤버 조회 (FCM 토큰이 있는 멤버만)
            List<Member> members = memberRepository.findByCompanyIdAndFcmTokenIsNotNull(company.getId());

            log.info("[Notice Service] 알림 전송 대상 멤버 수: {}", members.size());

            for (Member member : members) {
                try {
                    FCMNotificationRequestDTO request = FCMNotificationRequestDTO.builder()
                            .recipientToken(member.getFcmToken())
                            .title("[공지] " + notice.getTitle())
                            .message(truncateContent(notice.getContent(), 100))
                            .recipientUserId(member.getId().toString())
                            .recipientUserName(member.getName())
                            .type("NOTICE")
                            .relatedEntityId(notice.getId())
                            .relatedEntityType("notice")
                            .data(Map.of(
                                    "type", "notice",
                                    "noticeId", String.valueOf(notice.getId()),
                                    "priority", notice.getPriority().name()
                            ))
                            .build();

                    notificationService.sendAndSaveNotification(request);
                    log.debug("[Notice Service] FCM 전송 성공: memberId={}", member.getId());

                } catch (Exception e) {
                    log.error("[Notice Service] FCM 전송 실패: memberId={}, error={}",
                            member.getId(), e.getMessage());
                }
            }

            log.info("[Notice Service] 공지사항 FCM 알림 전송 완료: noticeId={}", notice.getId());

        } catch (Exception e) {
            log.error("[Notice Service] 공지사항 알림 전송 중 오류: {}", e.getMessage());
        }
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    private Notice.NoticePriority parsePriority(String priority) {
        if (priority == null) {
            return Notice.NoticePriority.NORMAL;
        }
        try {
            return Notice.NoticePriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[Notice Service] 알 수 없는 우선순위: {}", priority);
            return Notice.NoticePriority.NORMAL;
        }
    }

    private Notice.NoticeStatus parseStatus(String status) {
        if (status == null) {
            return Notice.NoticeStatus.DRAFT;
        }
        try {
            return Notice.NoticeStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[Notice Service] 알 수 없는 상태: {}", status);
            return Notice.NoticeStatus.DRAFT;
        }
    }
}