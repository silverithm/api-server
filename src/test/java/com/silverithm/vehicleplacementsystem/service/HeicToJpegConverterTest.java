package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("HEIC → JPEG 변환기 테스트")
class HeicToJpegConverterTest {

    @Test
    @DisplayName("HEIC/HEIF 계열만 변환 대상으로 본다")
    void detectsHeicContentTypes() {
        assertTrue(HeicToJpegConverter.isHeic("image/heic"));
        assertTrue(HeicToJpegConverter.isHeic("image/heif"));
        assertTrue(HeicToJpegConverter.isHeic("image/heic-sequence"));
        assertTrue(HeicToJpegConverter.isHeic("IMAGE/HEIC; charset=binary"));

        assertFalse(HeicToJpegConverter.isHeic("image/jpeg"));
        assertFalse(HeicToJpegConverter.isHeic("image/avif"));
        assertFalse(HeicToJpegConverter.isHeic("application/octet-stream"));
        assertFalse(HeicToJpegConverter.isHeic(null));
    }

    @Test
    @DisplayName("파일명 확장자를 .jpg로 바꾼다 - 사용자가 보는 이름도 JPEG여야 한다")
    void rewritesFileNameExtension() {
        assertEquals("IMG_0001.jpg", HeicToJpegConverter.toJpegFileName("IMG_0001.HEIC"));
        assertEquals("사진.jpg", HeicToJpegConverter.toJpegFileName("사진.heif"));
        assertEquals("확장자없음.jpg", HeicToJpegConverter.toJpegFileName("확장자없음"));
        assertEquals("photo.jpg", HeicToJpegConverter.toJpegFileName(null));
    }

    @Test
    @DisplayName("변환기가 없는 서버에서는 null을 돌려준다 - 업로드는 계속돼야 한다")
    void returnsNullWhenBinaryMissing() {
        HeicToJpegConverter converter = new HeicToJpegConverter();
        ReflectionTestUtils.setField(converter, "command", "carev-heif-convert-존재하지-않음");

        assertFalse(converter.isAvailable());
        assertNull(converter.toJpeg(new byte[]{1, 2, 3, 4}));
    }

    @Test
    @DisplayName("빈 입력은 변환하지 않는다")
    void ignoresEmptyInput() {
        assertNull(new HeicToJpegConverter().toJpeg(null));
        assertNull(new HeicToJpegConverter().toJpeg(new byte[0]));
    }
}
