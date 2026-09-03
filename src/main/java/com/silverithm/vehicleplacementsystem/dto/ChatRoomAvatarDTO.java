package com.silverithm.vehicleplacementsystem.dto;

import lombok.*;

/**
 * 채팅방 목록에 겹쳐 보여줄 참여자 한 명.
 *
 * 카카오톡처럼 방 아이콘 자리에 참여자 얼굴을 모아 보여주기 위한 최소 정보만 담는다.
 * 이름은 사진이 없을 때 첫 글자로 그리는 데 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomAvatarDTO {
    private String userId;
    private String userName;
    private String profileImageUrl;
}
