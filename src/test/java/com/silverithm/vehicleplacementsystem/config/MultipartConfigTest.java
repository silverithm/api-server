package com.silverithm.vehicleplacementsystem.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Multipart 업로드 한도 테스트")
class MultipartConfigTest {

    private static final long MB = 1024L * 1024L;

    @Test
    @DisplayName("단일 파일 한도는 앱의 동영상 상한(100MB)과 같아야 한다")
    void allowsHundredMegabyteFile() {
        MultipartConfigElement config = new MultipartConfig().multipartConfigElement();

        // 앱(chat_room_screen.dart)이 100MB까지 올린다. 여기가 더 작으면 선생님은
        // 긴 영상을 한참 올린 끝에 실패를 본다.
        assertEquals(100 * MB, config.getMaxFileSize());
    }

    @Test
    @DisplayName("요청 한도는 파일 한도보다 커야 한다 - 폼 필드와 경계 문자열이 함께 실린다")
    void requestLimitLeavesRoomForFormFields() {
        MultipartConfigElement config = new MultipartConfig().multipartConfigElement();

        assertTrue(config.getMaxRequestSize() > config.getMaxFileSize(),
                "요청 한도가 파일 한도와 같으면 딱 100MB짜리 파일이 요청 한도에 걸려 튕긴다");
        assertEquals(120 * MB, config.getMaxRequestSize());
    }

    @Test
    @DisplayName("2MB를 넘는 파일은 힙이 아니라 디스크로 흐른다")
    void spillsLargeFilesToDisk() {
        MultipartConfigElement config = new MultipartConfig().multipartConfigElement();

        // 이 값이 크면 100MB 동영상이 톰캣 힙에 통째로 앉는다. 메모리 3.8GB 서버라 그러면 안 된다.
        assertEquals(2 * MB, config.getFileSizeThreshold());
    }
}
