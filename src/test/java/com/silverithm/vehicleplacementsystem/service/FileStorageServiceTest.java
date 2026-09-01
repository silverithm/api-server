package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@DisplayName("채팅 이미지 썸네일 생성 테스트")
class FileStorageServiceTest {

    private FileStorageService fileStorageService;
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService();
        s3Client = mock(S3Client.class);

        // S3 자격 증명 없이도 테스트할 수 있도록 @PostConstruct가 채우는 상태를 직접 주입한다.
        ReflectionTestUtils.setField(fileStorageService, "s3Client", s3Client);
        ReflectionTestUtils.setField(fileStorageService, "s3Enabled", true);
        ReflectionTestUtils.setField(fileStorageService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(fileStorageService, "folder", "test/");
    }

    @Test
    @DisplayName("긴 변이 640px보다 큰 이미지는 축소 썸네일을 만들어 S3에 올린다")
    void generatesThumbnailForLargeImage() throws Exception {
        // given: 1200x800 (긴 변 1200px) JPEG 이미지
        byte[] largeImageBytes = createJpegBytes(1200, 800);
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", largeImageBytes);

        when(s3Client, PutObjectResponse.builder().build());

        // when
        String thumbnailPath = fileStorageService.generateAndStoreThumbnail(file, "chat/1/abcd-uuid.jpg");

        // then: 원본 키에 _thumb.jpg 접미사가 붙은 경로가 반환되고 S3 업로드가 호출된다
        assertNotNull(thumbnailPath);
        assertEquals("chat/1/abcd-uuid_thumb.jpg", thumbnailPath);

        ArgumentCaptorHolder captured = capturePutObject(s3Client);
        assertEquals("test/chat/1/abcd-uuid_thumb.jpg", captured.key());
        assertEquals("image/jpeg", captured.contentType());

        // 축소본이 실제로 긴 변 640px로 줄었는지 픽셀 크기까지 확인한다
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(captured.bytes()));
        assertNotNull(thumbnail);
        assertEquals(640, Math.max(thumbnail.getWidth(), thumbnail.getHeight()));
        assertEquals(640, thumbnail.getWidth());
        assertEquals(427, thumbnail.getHeight()); // 1200x800 비율 유지 축소 결과
    }

    @Test
    @DisplayName("긴 변이 640px 이하인 이미지는 썸네일을 만들지 않는다")
    void skipsThumbnailForSmallImage() throws Exception {
        byte[] smallImageBytes = createJpegBytes(400, 300);
        MockMultipartFile file = new MockMultipartFile(
                "file", "small.jpg", "image/jpeg", smallImageBytes);

        String thumbnailPath = fileStorageService.generateAndStoreThumbnail(file, "chat/1/small-uuid.jpg");

        assertNull(thumbnailPath);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("이미지로 디코딩할 수 없는 파일이면 예외 없이 null을 반환한다")
    void returnsNullForCorruptImage() {
        byte[] garbage = "이건 이미지가 아닙니다".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "broken.jpg", "image/jpeg", garbage);

        String thumbnailPath = fileStorageService.generateAndStoreThumbnail(file, "chat/1/broken-uuid.jpg");

        assertNull(thumbnailPath);
        verifyNoInteractions(s3Client);
    }

    private byte[] createJpegBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    // Mockito의 when(...).thenReturn(...) 대신 static import 충돌을 피하려고 감싼 헬퍼
    private void when(S3Client client, PutObjectResponse response) {
        org.mockito.Mockito.when(client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(response);
    }

    private ArgumentCaptorHolder capturePutObject(S3Client client) {
        org.mockito.ArgumentCaptor<PutObjectRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        org.mockito.ArgumentCaptor<software.amazon.awssdk.core.sync.RequestBody> bodyCaptor =
                org.mockito.ArgumentCaptor.forClass(software.amazon.awssdk.core.sync.RequestBody.class);
        verify(client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        try {
            byte[] bytes = bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes();
            return new ArgumentCaptorHolder(requestCaptor.getValue().key(), requestCaptor.getValue().contentType(), bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record ArgumentCaptorHolder(String key, String contentType, byte[] bytes) {
    }
}
