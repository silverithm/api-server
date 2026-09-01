package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.service.ChatCallerResolver;
import com.silverithm.vehicleplacementsystem.service.ChatService;
import com.silverithm.vehicleplacementsystem.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ChatController {

    private final ChatService chatService;
    /** '나'를 가리키는 값은 요청이 아니라 토큰에서 정한다 (ChatCallerResolver 참고) */
    private final ChatCallerResolver chatCallerResolver;
    private final FileStorageService fileStorageService;

    @Value("${app.base-url:https://silverithm.site}")
    private String baseUrl;

    // ==================== 채팅방 API ====================

    /**
     * 채팅방 목록 조회
     */
    @GetMapping("/rooms")
    public ResponseEntity<Map<String, Object>> getChatRooms(
            @RequestParam Long companyId,
            @RequestParam String userId) {

        try {
            String callerId = chatCallerResolver.resolveSelf(userId);
            log.info("[Chat API] 채팅방 목록 조회: companyId={}, userId={}", companyId, callerId);

            List<ChatRoomDTO> rooms = chatService.getChatRooms(companyId, callerId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("rooms", rooms));

        } catch (Exception e) {
            log.error("[Chat API] 채팅방 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "채팅방 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 채팅방 생성
     */
    @PostMapping("/rooms")
    public ResponseEntity<Map<String, Object>> createChatRoom(
            @RequestParam Long companyId,
            @Valid @RequestBody ChatRoomCreateRequest request) {

        try {
            // 만든 사람은 토큰으로 정한다. 옛 앱은 관리자도 원시 숫자를 보내는데, 그대로 두면
            // 참가자 행이 남(같은 숫자 id의 직원)으로 들어가 정작 만든 본인이 방을 못 본다.
            String creatorId = chatCallerResolver.resolveSelf(request.getCreatedBy());
            if (!creatorId.equals(request.getCreatedBy())) {
                List<String> fixed = new ArrayList<>();
                for (String participantId : request.getParticipantIds()) {
                    fixed.add(participantId.equals(request.getCreatedBy()) ? creatorId : participantId);
                }
                request.setParticipantIds(fixed);
                request.setCreatedBy(creatorId);
            }
            // 만든 사람이 빠져 있으면 넣어준다 (자기 방을 못 보는 일이 없게)
            if (!request.getParticipantIds().contains(creatorId)) {
                List<String> withCreator = new ArrayList<>(request.getParticipantIds());
                withCreator.add(creatorId);
                request.setParticipantIds(withCreator);
            }

            log.info("[Chat API] 채팅방 생성: companyId={}, name={}", companyId, request.getName());

            ChatRoomDTO room = chatService.createChatRoom(companyId, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "room", room,
                            "message", "채팅방이 생성되었습니다."
                    ));

        } catch (IllegalArgumentException e) {
            // 넣을 수 없는 사람(다른 기관·없는 계정)은 서버 오류가 아니라 잘못된 요청이다
            log.warn("[Chat API] 채팅방 생성 거절: companyId={}, {}", companyId, e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("[Chat API] 채팅방 생성 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "채팅방 생성 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 채팅방 상세 조회
     */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<Map<String, Object>> getChatRoom(@PathVariable Long roomId) {

        try {
            log.info("[Chat API] 채팅방 상세 조회: roomId={}", roomId);

            ChatRoomDTO room = chatService.getChatRoomDetail(roomId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("room", room));

        } catch (Exception e) {
            log.error("[Chat API] 채팅방 상세 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "채팅방 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 방 공지 설정 / 해제 — messageId를 주면 그 메시지를 상단에 고정하고, 없으면 공지를 내린다.
     */
    @PutMapping("/rooms/{roomId}/notice")
    public ResponseEntity<Map<String, Object>> updateChatRoomNotice(
            @PathVariable Long roomId,
            @RequestBody Map<String, Object> body) {

        try {
            Object rawId = body.get("messageId");
            Long messageId = rawId == null ? null : Long.valueOf(String.valueOf(rawId));
            String setByName = body.get("setByName") == null ? null : String.valueOf(body.get("setByName"));
            // 공지에 딸린 파일 메시지 (선택) — 배너에서 파일을 바로 열 수 있게 한다
            Object rawFileId = body.get("fileMessageId");
            Long fileMessageId = rawFileId == null ? null : Long.valueOf(String.valueOf(rawFileId));

            log.info("[Chat API] 방 공지 변경: roomId={}, messageId={}, fileMessageId={}", roomId, messageId, fileMessageId);

            ChatRoomDTO room = chatService.updateChatRoomNotice(roomId, messageId, setByName, fileMessageId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "room", room,
                            "message", messageId == null ? "공지를 내렸습니다." : "공지로 등록했습니다."
                    ));

        } catch (Exception e) {
            log.error("[Chat API] 방 공지 변경 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "공지 변경 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 채팅방 수정
     */
    @PutMapping("/rooms/{roomId}")
    public ResponseEntity<Map<String, Object>> updateChatRoom(
            @PathVariable Long roomId,
            @RequestBody ChatRoomUpdateRequest request) {

        try {
            log.info("[Chat API] 채팅방 수정: roomId={}", roomId);

            ChatRoomDTO room = chatService.updateChatRoom(roomId, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "room", room,
                            "message", "채팅방이 수정되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Chat API] 채팅방 수정 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "채팅방 수정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 채팅방 나가기
     */
    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<Map<String, Object>> leaveChatRoom(
            @PathVariable Long roomId,
            @RequestParam(required = false) String userId,
            @RequestBody(required = false) Map<String, String> request) {

        try {
            // 쿼리 파라미터 또는 request body에서 userId 가져오기
            String effectiveUserId = chatCallerResolver.resolveSelf(userId);
            if (effectiveUserId == null && request != null) {
                effectiveUserId = request.get("userId");
            }

            log.info("[Chat API] 채팅방 나가기: roomId={}, userId={}", roomId, effectiveUserId);

            chatService.leaveChatRoom(roomId, effectiveUserId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "채팅방을 나갔습니다."
                    ));

        } catch (Exception e) {
            log.error("[Chat API] 채팅방 나가기 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "채팅방 나가기 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 채팅방 삭제
     */
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Map<String, Object>> deleteChatRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            log.info("[Chat API] 채팅방 삭제: roomId={}", roomId);

            chatService.deleteChatRoom(roomId, userDetails != null ? userDetails.getUsername() : null);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "채팅방이 삭제되었습니다."
                    ));

        } catch (SecurityException e) {
            log.error("[Chat API] 채팅방 삭제 권한 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.error("[Chat API] 채팅방 삭제 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("[Chat API] 채팅방 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "채팅방 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // ==================== 참가자 API ====================

    /**
     * 참가자 목록 조회
     */
    @GetMapping("/rooms/{roomId}/participants")
    public ResponseEntity<Map<String, Object>> getParticipants(@PathVariable Long roomId) {

        try {
            log.info("[Chat API] 참가자 목록 조회: roomId={}", roomId);

            List<ChatParticipantDTO> participants = chatService.getParticipants(roomId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("participants", participants));

        } catch (Exception e) {
            log.error("[Chat API] 참가자 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "참가자 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 참가자 추가
     */
    @PostMapping("/rooms/{roomId}/participants")
    public ResponseEntity<Map<String, Object>> addParticipants(
            @PathVariable Long roomId,
            @RequestBody Map<String, List<String>> request) {

        try {
            List<String> userIds = request.get("userIds");
            log.info("[Chat API] 참가자 추가: roomId={}, userIds={}", roomId, userIds);

            List<ChatParticipantDTO> added = chatService.addParticipants(roomId, userIds);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "participants", added,
                            "message", "참가자가 추가되었습니다."
                    ));

        } catch (IllegalArgumentException e) {
            // 넣을 수 없는 사람(다른 기관·없는 계정)은 서버 오류가 아니라 잘못된 요청이다
            log.warn("[Chat API] 참가자 추가 거절: roomId={}, {}", roomId, e.getMessage());
            return ResponseEntity.badRequest()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("[Chat API] 참가자 추가 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "참가자 추가 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 참가자 제거 (강퇴)
     */
    @DeleteMapping("/rooms/{roomId}/participants/{userId}")
    public ResponseEntity<Map<String, Object>> removeParticipant(
            @PathVariable Long roomId,
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean isKicked) {

        try {
            log.info("[Chat API] 참가자 제거: roomId={}, userId={}, isKicked={}", roomId, userId, isKicked);

            chatService.removeParticipant(roomId, userId, isKicked);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", isKicked ? "참가자가 강퇴되었습니다." : "참가자가 나갔습니다."
                    ));

        } catch (Exception e) {
            log.error("[Chat API] 참가자 제거 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "참가자 제거 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // ==================== 메시지 API ====================

    /**
     * 메시지 목록 조회
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String userId) {

        try {
            String callerId = chatCallerResolver.resolveSelf(userId);
            log.info("[Chat API] 메시지 목록 조회: roomId={}, page={}, size={}, userId={}", roomId, page, size, callerId);

            List<ChatMessageDTO> messages = chatService.getMessages(roomId, page, size, callerId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "messages", messages,
                            "hasMore", messages.size() == size
                    ));

        } catch (Exception e) {
            log.error("[Chat API] 메시지 목록 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "메시지 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 메시지 주변 조회 — 검색 결과 등에서 현재 화면에 없는 과거 메시지로 바로 이동할 때 쓴다.
     * messageId를 가운데 두고 앞뒤로 size/2씩 가져온다. 응답 모양은 목록 조회(GET /messages)와 같다.
     */
    @GetMapping("/rooms/{roomId}/messages/around")
    public ResponseEntity<Map<String, Object>> getMessagesAround(
            @PathVariable Long roomId,
            @RequestParam Long messageId,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String userId) {

        try {
            String callerId = chatCallerResolver.resolveSelf(userId);
            log.info("[Chat API] 메시지 주변 조회: roomId={}, messageId={}, size={}, userId={}",
                    roomId, messageId, size, callerId);

            ChatMessagesAroundDTO result = chatService.getMessagesAround(roomId, messageId, size, callerId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "messages", result.getMessages(),
                            "hasBefore", result.isHasBefore(),
                            "hasAfter", result.isHasAfter()
                    ));

        } catch (SecurityException e) {
            log.error("[Chat API] 메시지 주변 조회 권한 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.error("[Chat API] 메시지 주변 조회 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .headers(getCorsHeaders())
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("[Chat API] 메시지 주변 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "메시지 주변 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 메시지 전송
     */
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable Long roomId,
            @Valid @RequestBody ChatMessageCreateRequest request) {

        try {
            request.setSenderId(chatCallerResolver.resolveSelf(request.getSenderId()));
            log.info("[Chat API] 메시지 전송: roomId={}, senderId={}", roomId, request.getSenderId());

            ChatMessageDTO message = chatService.sendMessage(roomId, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", message
                    ));

        } catch (Exception e) {
            log.error("[Chat API] 메시지 전송 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "메시지 전송 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 메시지 삭제
     */
    @DeleteMapping("/rooms/{roomId}/messages/{messageId}")
    public ResponseEntity<Map<String, Object>> deleteMessage(
            @PathVariable Long roomId,
            @PathVariable Long messageId) {

        try {
            // '나'는 요청이 아니라 토큰에서 정한다 — 삭제 권한이 여기에 걸려 있어서
            // 클라이언트가 보낸 값을 믿으면 남의 메시지를 지울 수 있다 ([[ChatCallerResolver]])
            String callerId = chatCallerResolver.currentChatUserId();
            log.info("[Chat API] 메시지 삭제: roomId={}, messageId={}, caller={}", roomId, messageId, callerId);

            chatService.deleteMessage(roomId, messageId, callerId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "메시지가 삭제되었습니다."
                    ));

        } catch (CustomException e) {
            // 권한 없음(403)까지 여기서 삼키면 클라이언트에는 500으로 보인다 —
            // 상태 코드를 들고 있는 예외는 전역 핸들러가 그대로 내보내게 둔다
            throw e;
        } catch (Exception e) {
            log.error("[Chat API] 메시지 삭제 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "메시지 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // ==================== 리액션 API ====================

    /**
     * 리액션 토글 (추가/삭제)
     */
    @PostMapping("/rooms/{roomId}/messages/{messageId}/reactions")
    public ResponseEntity<Map<String, Object>> toggleReaction(
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @RequestBody Map<String, String> request) {

        try {
            String userId = chatCallerResolver.resolveSelf(request.get("userId"));
            String userName = request.get("userName");
            String emoji = request.get("emoji");

            log.info("[Chat API] 리액션 토글: roomId={}, messageId={}, userId={}, emoji={}",
                    roomId, messageId, userId, emoji);

            ChatReactionDTO result = chatService.toggleReaction(roomId, messageId, userId, userName, emoji);

            if (result != null) {
                return ResponseEntity.ok()
                        .headers(getCorsHeaders())
                        .body(Map.of(
                                "success", true,
                                "action", "added",
                                "reaction", result
                        ));
            } else {
                return ResponseEntity.ok()
                        .headers(getCorsHeaders())
                        .body(Map.of(
                                "success", true,
                                "action", "removed"
                        ));
            }

        } catch (Exception e) {
            log.error("[Chat API] 리액션 토글 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "리액션 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 메시지 리액션 목록 조회
     */
    @GetMapping("/rooms/{roomId}/messages/{messageId}/reactions")
    public ResponseEntity<Map<String, Object>> getReactions(
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @RequestParam(required = false) String userId) {

        try {
            log.info("[Chat API] 리액션 조회: roomId={}, messageId={}", roomId, messageId);

            var reactions = chatService.getReactions(messageId, chatCallerResolver.resolveSelf(userId));

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("reactions", reactions));

        } catch (Exception e) {
            log.error("[Chat API] 리액션 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "리액션 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // ==================== 읽음 처리 API ====================

    /**
     * 읽음 처리
     */
    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable Long roomId,
            @RequestBody Map<String, Object> request) {

        try {
            String userId = chatCallerResolver.resolveSelf((String) request.get("userId"));
            String userName = (String) request.get("userName");
            Long lastMessageId = Long.valueOf(request.get("lastMessageId").toString());

            log.info("[Chat API] 읽음 처리: roomId={}, userId={}, lastMessageId={}", roomId, userId, lastMessageId);

            chatService.markAsRead(roomId, userId, userName, lastMessageId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "읽음 처리가 완료되었습니다."
                    ));

        } catch (Exception e) {
            log.error("[Chat API] 읽음 처리 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "읽음 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 메시지 읽은 사람 목록
     */
    @GetMapping("/rooms/{roomId}/messages/{messageId}/readers")
    public ResponseEntity<Map<String, Object>> getMessageReaders(
            @PathVariable Long roomId,
            @PathVariable Long messageId) {

        try {
            log.info("[Chat API] 메시지 읽은 사람 조회: roomId={}, messageId={}", roomId, messageId);

            List<ChatMessageReaderDTO> readers = chatService.getMessageReaders(roomId, messageId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("readers", readers));

        } catch (Exception e) {
            log.error("[Chat API] 메시지 읽은 사람 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "읽은 사람 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // ==================== 미디어 API ====================

    /**
     * 공유된 미디어 조회 (/media 엔드포인트)
     */
    @GetMapping("/rooms/{roomId}/media")
    public ResponseEntity<Map<String, Object>> getSharedMedia(
            @PathVariable Long roomId,
            @RequestParam(required = false) String type) {

        try {
            log.info("[Chat API] 공유된 미디어 조회: roomId={}, type={}", roomId, type);

            List<ChatMessageDTO> media = chatService.getSharedMedia(roomId, type);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("media", media));

        } catch (Exception e) {
            log.error("[Chat API] 공유된 미디어 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "미디어 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 방 안 메시지 검색
     */
    @GetMapping("/rooms/{roomId}/messages/search")
    public ResponseEntity<Map<String, Object>> searchMessages(
            @PathVariable Long roomId,
            @RequestParam String keyword) {

        try {
            log.info("[Chat API] 메시지 검색: roomId={}", roomId);

            List<ChatMessageDTO> messages = chatService.searchMessages(roomId, keyword);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("messages", messages));

        } catch (Exception e) {
            log.error("[Chat API] 메시지 검색 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "메시지 검색 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 공유된 파일 조회 (/files 엔드포인트 - 프론트엔드 호환)
     */
    @GetMapping("/rooms/{roomId}/files")
    public ResponseEntity<Map<String, Object>> getSharedFiles(
            @PathVariable Long roomId,
            @RequestParam(required = false) String type) {

        try {
            log.info("[Chat API] 공유된 파일 조회: roomId={}, type={}", roomId, type);

            List<ChatMessageDTO> files = chatService.getSharedMedia(roomId, type);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("files", files));

        } catch (Exception e) {
            log.error("[Chat API] 공유된 파일 조회 오류:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "파일 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 파일 업로드 및 메시지 전송
     */
    @PostMapping("/rooms/{roomId}/files")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @PathVariable Long roomId,
            @RequestParam("file") MultipartFile file,
            @RequestParam String senderId,
            @RequestParam String senderName) {

        try {
            senderId = chatCallerResolver.resolveSelf(senderId);
            log.info("[Chat API] 파일 업로드 시작: roomId={}, fileName={}, fileSize={}, senderId={}",
                    roomId, file.getOriginalFilename(), file.getSize(), senderId);

            if (file.isEmpty()) {
                log.warn("[Chat API] 빈 파일 업로드 시도");
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .body(Map.of("error", "파일이 비어있습니다."));
            }

            // 파일 저장
            log.info("[Chat API] FileStorageService 호출 시작");
            String filePath = fileStorageService.storeFile(file, "chat/" + roomId);
            log.info("[Chat API] 파일 저장 완료: filePath={}", filePath);

            // S3 URL 직접 사용 (서버 부하 감소, 더 빠른 다운로드)
            String fileUrl = fileStorageService.getFileUrl(filePath);
            log.info("[Chat API] S3 파일 URL 생성: {}", fileUrl);

            // 파일 타입 결정
            String contentType = file.getContentType();
            String messageType = "FILE";
            if (contentType != null && contentType.startsWith("image/")) {
                messageType = "IMAGE";
            }
            log.info("[Chat API] 메시지 타입: {}, contentType: {}", messageType, contentType);

            // 이미지면 채팅 목록에서 빠르게 받을 수 있도록 축소 썸네일을 함께 만든다.
            // 실패해도(깨진 이미지 등) 업로드 자체는 계속 진행한다 - thumbnailUrl만 null.
            String thumbnailUrl = null;
            if (contentType != null && contentType.startsWith("image/")) {
                String thumbnailPath = fileStorageService.generateAndStoreThumbnail(file, filePath);
                if (thumbnailPath != null) {
                    thumbnailUrl = fileStorageService.getFileUrl(thumbnailPath);
                    log.info("[Chat API] 썸네일 URL 생성: {}", thumbnailUrl);
                }
            }

            // 메시지 생성 요청
            ChatMessageCreateRequest messageRequest = ChatMessageCreateRequest.builder()
                    .senderId(senderId)
                    .senderName(senderName)
                    .type(messageType)
                    .content(file.getOriginalFilename())
                    .fileUrl(fileUrl)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .mimeType(contentType)
                    .thumbnailUrl(thumbnailUrl)
                    .build();

            log.info("[Chat API] ChatService.sendMessage 호출");
            ChatMessageDTO message = chatService.sendMessage(roomId, messageRequest);
            log.info("[Chat API] 파일 메시지 전송 완료: messageId={}", message.getId());

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", message,
                            "fileUrl", fileUrl
                    ));

        } catch (Exception e) {
            log.error("[Chat API] 파일 업로드 오류 - 상세: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .headers(getCorsHeaders())
                .build();
    }

    private HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        return headers;
    }
}
