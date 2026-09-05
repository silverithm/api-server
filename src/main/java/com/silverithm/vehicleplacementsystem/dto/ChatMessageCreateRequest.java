package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageCreateRequest {

    @NotBlank(message = "발신자 ID는 필수입니다")
    private String senderId;

    @NotBlank(message = "발신자 이름은 필수입니다")
    private String senderName;

    private String senderPosition;

    private String type; // TEXT, IMAGE, FILE, SYSTEM

    private String content;

    // 파일 메시지용
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;

    // 이미지 축소 썸네일 URL (생성 실패 시 null)
    private String thumbnailUrl;

    // 답글 대상 메시지 ID
    private Long replyToId;

    /**
     * 한 번의 동작으로 여러 장을 보낼 때의 묶음 정보.
     *
     * 사진 열 장을 한 번에 고르면 메시지는 열 건으로 나뉘어 오지만, 받는 사람 입장에서는
     * 한 번 보낸 것이다. 알림까지 열 번 울리면 화면에 묶음으로 보이는 것과 앞뒤가 맞지 않는다.
     * 그래서 보내는 쪽이 "이건 5장 중 3번째"라고 알려주고, 서버는 마지막 장이 올라온 뒤에
     * "사진 5장" 알림을 한 번만 보낸다.
     *
     * 없으면(구버전 앱, 한 장짜리 전송) 지금까지처럼 메시지마다 알림이 나간다.
     */
    private String batchId;
    private Integer batchSize;
    private Integer batchIndex;
}
