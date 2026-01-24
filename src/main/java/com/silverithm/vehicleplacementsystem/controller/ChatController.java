package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.*;
import com.silverithm.vehicleplacementsystem.service.ChatService;
import com.silverithm.vehicleplacementsystem.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Validated
public class ChatController {

    private final ChatService chatService;
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
            log.info("[Chat API] 채팅방 목록 조회: companyId={}, userId={}", companyId, userId);

            List<ChatRoomDTO> rooms = chatService.getChatRooms(companyId, userId);

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
            log.info("[Chat API] 채팅방 생성: companyId={}, name={}", companyId, request.getName());

            ChatRoomDTO room = chatService.createChatRoom(companyId, request);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "room", room,
                            "message", "채팅방이 생성되었습니다."
                    ));

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
            String effectiveUserId = userId;
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
    public ResponseEntity<Map<String, Object>> deleteChatRoom(@PathVariable Long roomId) {

        try {
            log.info("[Chat API] 채팅방 삭제: roomId={}", roomId);

            chatService.deleteChatRoom(roomId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "채팅방이 삭제되었습니다."
                    ));

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
            @RequestParam(defaultValue = "50") int size) {

        try {
            log.info("[Chat API] 메시지 목록 조회: roomId={}, page={}, size={}", roomId, page, size);

            List<ChatMessageDTO> messages = chatService.getMessages(roomId, page, size);

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
     * 메시지 전송
     */
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable Long roomId,
            @Valid @RequestBody ChatMessageCreateRequest request) {

        try {
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
            log.info("[Chat API] 메시지 삭제: roomId={}, messageId={}", roomId, messageId);

            chatService.deleteMessage(roomId, messageId);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "message", "메시지가 삭제되었습니다."
                    ));

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
            String userId = request.get("userId");
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

            var reactions = chatService.getReactions(messageId, userId);

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
            String userId = (String) request.get("userId");
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
