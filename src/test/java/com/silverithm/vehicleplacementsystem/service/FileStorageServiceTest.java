package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        // 변환기는 테스트에서 명시적으로 갈아 끼운다. 기본값은 "이 서버엔 heif-convert가 없다"로 두어
        // 개발 기계에 heif-convert가 깔려 있든 없든 결과가 같게 한다.
        ReflectionTestUtils.setField(fileStorageService, "heicToJpegConverter", unavailableConverter());
    }

    /** heif-convert가 없는 서버를 흉내낸다. */
    private HeicToJpegConverter unavailableConverter() {
        return new HeicToJpegConverter() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public byte[] toJpeg(byte[] heicContent) {
                return null;
            }
        };
    }

    /** 변환에 성공하는 서버를 흉내낸다. */
    private HeicToJpegConverter converterReturning(byte[] jpeg) {
        return new HeicToJpegConverter() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public byte[] toJpeg(byte[] heicContent) {
                return jpeg;
            }
        };
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

    // ---------------------------------------------------------------
    // Content-Type — 확장자별 전체 표는 FileContentTypeResolverTest에 있다.
    // 여기서는 S3에 실제로 어떤 값이 박히는지(서비스 레벨)를 확인한다.
    // ---------------------------------------------------------------

    @Test
    @DisplayName("heic 사진은 image/heic로 S3에 올라간다")
    void storesHeicWithImageContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "IMG_0001.HEIC", "image/heic", heicBytes());
        when(s3Client, PutObjectResponse.builder().build());

        String storedPath = fileStorageService.storeFile(file, "chat/1");

        assertTrue(storedPath.startsWith("chat/1/"));
        assertTrue(storedPath.endsWith(".HEIC"));
        assertEquals("image/heic", capturePutObject(s3Client).contentType());
    }

    @Test
    @DisplayName("확장자가 거짓말해도 실제 내용대로 올라간다 - .jpg로 저장된 PNG")
    void storesByActualContentNotExtension() throws Exception {
        byte[] actuallyPng = createImageBytes(10, 10, "png");
        MockMultipartFile file = new MockMultipartFile(
                "file", "KakaoTalk_20260902.jpg", "image/jpeg", actuallyPng);
        when(s3Client, PutObjectResponse.builder().build());

        fileStorageService.storeFile(file, "chat/1");

        assertEquals("image/png", capturePutObject(s3Client).contentType());
    }

    @Test
    @DisplayName("구버전 앱이 octet-stream으로 보낸 heic도 이미지로 올라간다")
    void storesHeicEvenWhenClientSaysOctetStream() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "IMG_0002.heic", "application/octet-stream", heicBytes());
        when(s3Client, PutObjectResponse.builder().build());

        fileStorageService.storeFile(file, "chat/1");

        assertEquals("image/heic", capturePutObject(s3Client).contentType());
    }

    @Test
    @DisplayName("probeContentType은 앞부분만 읽고도 실제 포맷을 알아낸다")
    void probeReadsOnlyTheHead() {
        MockMultipartFile heic = new MockMultipartFile(
                "file", "IMG_0003.jpg", "application/octet-stream", heicBytes());
        assertEquals("image/heic", fileStorageService.probeContentType(heic));

        MockMultipartFile document = new MockMultipartFile(
                "file", "계약서.hwp", null, "이건 이미지가 아닙니다".getBytes());
        assertEquals("application/x-hwp", fileStorageService.probeContentType(document));
    }

    @Test
    @DisplayName("서명 base64(png 바이트)는 image/png로 올라간다")
    void storesSignatureBytesAsPng() throws Exception {
        when(s3Client, PutObjectResponse.builder().build());

        fileStorageService.storeBytes(createImageBytes(4, 4, "png"), ".png", "signatures");

        assertEquals("image/png", capturePutObject(s3Client).contentType());
    }

    @Test
    @DisplayName("heic는 ImageIO가 못 읽으므로 썸네일 없이(null) 조용히 넘어간다")
    void skipsThumbnailForUndecodableHeic() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "IMG_0001.HEIC", "image/heic", heicBytes());

        String thumbnailPath = fileStorageService.generateAndStoreThumbnail(file, "chat/1/uuid.heic");

        // 썸네일이 없어도 업로드는 성공해야 하고, 원본은 image/heic로 이미 올라가 있다.
        assertNull(thumbnailPath);
        verifyNoInteractions(s3Client);
    }

    // ---------------------------------------------------------------
    // HEIC → JPEG 변환 (크롬·엣지·파이어폭스는 HEIC를 렌더링하지 못한다)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("HEIC 업로드는 JPEG 사본을 만들어 그쪽을 대표로 돌려주고, 원본도 그대로 남긴다")
    void convertsHeicUploadToJpegCopy() throws Exception {
        byte[] jpeg = createJpegBytes(4080, 3060);
        ReflectionTestUtils.setField(fileStorageService, "heicToJpegConverter", converterReturning(jpeg));
        MockMultipartFile file = new MockMultipartFile(
                "file", "IMG_0001.HEIC", "application/octet-stream", heicBytes());
        when(s3Client, PutObjectResponse.builder().build());

        FileStorageService.StoredUpload stored = fileStorageService.storeUpload(file, "chat/1");

        // 대표는 JPEG - 파일명·Content-Type·크기가 모두 JPEG 기준이어야 웹에서 열린다
        assertTrue(stored.isConverted());
        assertTrue(stored.path().endsWith(".jpg"));
        assertEquals("IMG_0001.jpg", stored.fileName());
        assertEquals("image/jpeg", stored.contentType());
        assertEquals(jpeg.length, stored.size());

        // 원본은 지우지 않는다 - 되돌릴 것이 남아 있어야 한다
        assertTrue(stored.originalPath().endsWith(".HEIC"));
        assertEquals("image/heic", stored.originalContentType());

        // S3에는 원본과 JPEG 두 개가 올라간다
        java.util.List<PutObjectRequest> puts = capturePutObjects(s3Client, 2);
        assertEquals("image/heic", puts.get(0).contentType());
        assertEquals("image/jpeg", puts.get(1).contentType());
        assertEquals("test/" + stored.originalPath(), puts.get(0).key());
        assertEquals("test/" + stored.path(), puts.get(1).key());
    }

    @Test
    @DisplayName("변환이 실패해도 업로드는 성공한다 - 원본을 그대로 쓴다")
    void keepsOriginalWhenConversionFails() throws Exception {
        // 기본 스텁이 "변환기 없음"이다 (운영에서 패키지가 빠졌을 때와 같은 상황)
        MockMultipartFile file = new MockMultipartFile(
                "file", "IMG_0002.heic", "image/heic", heicBytes());
        when(s3Client, PutObjectResponse.builder().build());

        FileStorageService.StoredUpload stored = fileStorageService.storeUpload(file, "chat/1");

        assertFalse(stored.isConverted());
        assertTrue(stored.path().endsWith(".heic"));
        assertEquals("image/heic", stored.contentType());
        assertEquals("IMG_0002.heic", stored.fileName());
        assertEquals("image/heic", capturePutObject(s3Client).contentType());
    }

    @Test
    @DisplayName("JPEG 사본이 생기면 썸네일도 그 사본에서 만들어진다")
    void buildsThumbnailFromConvertedJpeg() throws Exception {
        byte[] jpeg = createJpegBytes(1200, 800);
        ReflectionTestUtils.setField(fileStorageService, "heicToJpegConverter", converterReturning(jpeg));
        MockMultipartFile file = new MockMultipartFile(
                "file", "IMG_0003.heic", "image/heic", heicBytes());
        when(s3Client, PutObjectResponse.builder().build());

        FileStorageService.StoredUpload stored = fileStorageService.storeUpload(file, "chat/1");
        String thumbnailPath = fileStorageService.generateAndStoreThumbnail(stored.content(), stored.path());

        // HEIC 원본으로는 만들 수 없던 썸네일이 JPEG 사본에서는 만들어진다
        assertNotNull(thumbnailPath);
        assertTrue(thumbnailPath.endsWith("_thumb.jpg"));
    }

    @Test
    @DisplayName("HEIC가 아닌 파일은 변환을 거치지 않고 그대로 저장된다")
    void leavesNonHeicUploadUntouched() throws Exception {
        byte[] png = createImageBytes(10, 10, "png");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png);
        when(s3Client, PutObjectResponse.builder().build());

        FileStorageService.StoredUpload stored = fileStorageService.storeUpload(file, "chat/1");

        assertFalse(stored.isConverted());
        assertEquals("image/png", stored.contentType());
        assertEquals("photo.png", stored.fileName());
        assertEquals(png.length, stored.size());
    }

    /** ftyp 상자를 갖춘 최소한의 HEIC 헤더. 자바로는 HEIC를 인코딩할 수 없어 시그니처만 만든다. */
    private byte[] heicBytes() {
        byte[] bytes = new byte[32];
        bytes[3] = 24;
        byte[] header = "ftypheic".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        System.arraycopy(header, 0, bytes, 4, header.length);
        return bytes;
    }

    private byte[] createImageBytes(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
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

    /** PutObject가 여러 번 호출된 경우 호출 순서대로 요청을 돌려준다. */
    private java.util.List<PutObjectRequest> capturePutObjects(S3Client client, int times) {
        org.mockito.ArgumentCaptor<PutObjectRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, org.mockito.Mockito.times(times))
                .putObject(requestCaptor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        return requestCaptor.getAllValues();
    }

    private record ArgumentCaptorHolder(String key, String contentType, byte[] bytes) {
    }
}
