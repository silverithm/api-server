package com.silverithm.vehicleplacementsystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

@Service
@Slf4j
public class FileStorageService {

    /** 채팅 이미지 썸네일의 긴 변 최대 길이(px). 이보다 작은 원본은 썸네일을 만들지 않는다. */
    private static final int THUMBNAIL_MAX_SIDE = 640;
    /** 썸네일 JPEG 압축 품질(0.0~1.0). */
    private static final float THUMBNAIL_QUALITY = 0.8f;

    @Value("${cloud.aws.s3.bucket:}")
    private String bucketName;

    @Value("${cloud.aws.s3.folder:}")
    private String folder;

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    @Value("${cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key:}")
    private String secretKey;

    private S3Client s3Client;
    private boolean s3Enabled = false;

    @PostConstruct
    public void init() {
        // S3 설정이 없으면 비활성화
        if (bucketName == null || bucketName.isBlank()) {
            log.warn("[FileStorage] S3 버킷이 설정되지 않아 파일 저장 기능이 비활성화됩니다.");
            return;
        }

        try {
            log.info("[FileStorage] Credentials 확인: accessKey={}, secretKey={}",
                    accessKey != null && !accessKey.isBlank() ? accessKey.substring(0, 8) + "..." : "NULL/EMPTY",
                    secretKey != null && !secretKey.isBlank() ? "SET" : "NULL/EMPTY");

            if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
                // 명시적 자격 증명 사용
                log.info("[FileStorage] 명시적 자격 증명 사용");
                AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
                this.s3Client = S3Client.builder()
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .build();
            } else {
                // 기본 자격 증명 체인 사용 (EC2 IAM Role 등)
                log.info("[FileStorage] 기본 자격 증명 체인 사용 (EC2 IAM Role 등)");
                this.s3Client = S3Client.builder()
                        .region(Region.of(region))
                        .build();
            }
            this.s3Enabled = true;
            log.info("[FileStorage] S3 클라이언트 초기화 완료: bucket={}, folder={}, region={}", bucketName, folder, region);
        } catch (Exception e) {
            log.error("[FileStorage] S3 클라이언트 초기화 실패", e);
            log.warn("[FileStorage] 파일 저장 기능이 비활성화됩니다.");
        }
    }

    private void checkS3Enabled() {
        if (!s3Enabled) {
            throw new IllegalStateException("S3 파일 저장 기능이 비활성화되어 있습니다. AWS 설정을 확인해주세요.");
        }
    }

    /**
     * 파일 저장
     * @param file 업로드할 파일
     * @param subDirectory 하위 디렉토리 (예: "templates", "attachments")
     * @return 저장된 파일 경로 (S3 key, folder prefix 제외)
     */
    public String storeFile(MultipartFile file, String subDirectory) throws IOException {
        checkS3Enabled();

        // 원본 파일명
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("파일명이 유효하지 않습니다.");
        }

        // 파일 확장자 추출
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf(".");
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
        }

        // UUID로 고유 파일명 생성
        String storedFileName = UUID.randomUUID().toString() + extension;

        // S3 key 생성 (folder prefix 포함)
        String relativePath = subDirectory + "/" + storedFileName;
        String s3Key = folder + relativePath;

        log.info("[FileStorage] PutObject 요청 시작: bucket={}, key={}, originalFile={}", bucketName, s3Key, originalFileName);

        try {
            // 업로드 바이트는 한 번만 읽어 Content-Type 판정과 업로드에 함께 쓴다.
            byte[] content = file.getBytes();

            // Content-Type 결정 — 확장자만 믿지 않고 파일 실제 내용을 먼저 본다.
            // 카톡·스캔 앱을 거치면 확장자가 내용과 다른 사진이 흔하고, 그때 확장자를 믿으면
            // 원본이 S3에서 이미지로 열리지 않는다.
            String contentType = resolveContentType(content, originalFileName, file.getContentType());
            if (log.isInfoEnabled() && !contentType.equals(determineContentType(extension))) {
                log.info("[FileStorage] 확장자와 실제 내용이 다릅니다 - 내용을 따릅니다: file={}, 확장자기준={}, 최종={}",
                        originalFileName, determineContentType(extension), contentType);
            }

            // S3에 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));

            log.info("[FileStorage] PutObject 성공: key={}, size={}bytes, contentType={}, returnPath={}",
                    s3Key, file.getSize(), contentType, relativePath);

            // folder prefix를 제외한 상대 경로 반환
            return relativePath;
        } catch (S3Exception e) {
            log.error("[FileStorage] PutObject 실패: key={}, statusCode={}, errorCode={}, errorMessage={}",
                    s3Key, e.statusCode(),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "N/A",
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
            throw new IOException("S3 파일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 바이트 배열 저장 (서명/직인 등 base64 디코드 이미지용)
     *
     * @param bytes        파일 내용
     * @param extension    확장자 (예: ".png")
     * @param subDirectory 하위 디렉토리 (예: "signatures", "seals")
     * @return 저장된 파일 경로 (S3 key, folder prefix 제외)
     */
    public String storeBytes(byte[] bytes, String extension, String subDirectory) throws IOException {
        checkS3Enabled();

        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("파일 내용이 비어 있습니다.");
        }

        String storedFileName = UUID.randomUUID().toString() + extension;
        String relativePath = subDirectory + "/" + storedFileName;
        String s3Key = folder + relativePath;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    // base64로 들어온 서명/직인 등 - 여기서도 내용을 먼저 본다.
                    .contentType(resolveContentType(bytes, extension, null))
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));

            log.info("[FileStorage] PutObject(bytes) 성공: key={}, size={}bytes", s3Key, bytes.length);
            return relativePath;
        } catch (S3Exception e) {
            log.error("[FileStorage] PutObject(bytes) 실패: key={}, {}", s3Key, e.getMessage());
            throw new IOException("S3 파일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 이미지 파일의 축소 썸네일(긴 변 640px, JPEG)을 만들어 S3에 저장한다.
     * 원본이 이미 충분히 작거나(긴 변 640px 이하) 디코딩할 수 없는 포맷이면 썸네일을 만들지 않고 null을 반환한다.
     * 채팅 파일 업로드는 썸네일이 없어도 성공해야 하므로 예외를 던지지 않고 로그만 남긴 뒤 null을 반환한다.
     *
     * @param file                 업로드된 원본 이미지 파일
     * @param originalRelativePath storeFile()이 반환한 원본 파일의 상대 경로(S3 key, folder prefix 제외)
     * @return 저장된 썸네일의 상대 경로. 만들지 않았거나 실패하면 null
     */
    public String generateAndStoreThumbnail(MultipartFile file, String originalRelativePath) {
        if (!s3Enabled) {
            return null;
        }

        try {
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original == null) {
                // 자바 표준 ImageIO는 heic/heif(아이폰 기본 포맷)와 webp를 읽지 못한다.
                // 썸네일 없이 원본을 쓰게 두고, 어떤 포맷이 걸렸는지는 로그로 남긴다.
                log.warn("[FileStorage] 썸네일 생성 스킵 - ImageIO가 디코딩할 수 없는 포맷: path={}, fileName={}, mimeType={}",
                        originalRelativePath, file.getOriginalFilename(), file.getContentType());
                return null;
            }

            int width = original.getWidth();
            int height = original.getHeight();
            int longSide = Math.max(width, height);
            if (longSide <= THUMBNAIL_MAX_SIDE) {
                // 이미 충분히 작으면 썸네일을 만들 필요가 없다.
                return null;
            }

            double scale = (double) THUMBNAIL_MAX_SIDE / longSide;
            int thumbWidth = Math.max(1, (int) Math.round(width * scale));
            int thumbHeight = Math.max(1, (int) Math.round(height * scale));

            BufferedImage thumbnail = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumbnail.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 원본에 투명 배경(PNG 등)이 있어도 JPEG에서 검게 나오지 않도록 흰 배경을 깐다.
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, thumbWidth, thumbHeight);
                g.drawImage(original, 0, 0, thumbWidth, thumbHeight, null);
            } finally {
                g.dispose();
            }

            byte[] thumbnailBytes = encodeJpeg(thumbnail, THUMBNAIL_QUALITY);
            String thumbnailRelativePath = buildThumbnailPath(originalRelativePath);
            uploadBytes(thumbnailRelativePath, thumbnailBytes, "image/jpeg");

            log.info("[FileStorage] 썸네일 생성 완료: original={}x{}, thumb={}x{}, path={}",
                    width, height, thumbWidth, thumbHeight, thumbnailRelativePath);
            return thumbnailRelativePath;
        } catch (Exception e) {
            // 썸네일 생성 실패가 채팅 파일 업로드 자체를 막으면 안 된다.
            log.error("[FileStorage] 썸네일 생성 실패: path={}, {}", originalRelativePath, e.getMessage(), e);
            return null;
        }
    }

    /** 원본 상대 경로에서 확장자를 떼고 "_thumb.jpg" 접미사를 붙인다. */
    private String buildThumbnailPath(String originalRelativePath) {
        int dotIndex = originalRelativePath.lastIndexOf(".");
        String base = dotIndex > 0 ? originalRelativePath.substring(0, dotIndex) : originalRelativePath;
        return base + "_thumb.jpg";
    }

    /** BufferedImage를 지정한 품질의 JPEG 바이트 배열로 인코딩한다. */
    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG ImageWriter를 찾을 수 없습니다.");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
                ios.flush();
                return baos.toByteArray();
            }
        } finally {
            writer.dispose();
        }
    }

    /** 이미 만들어진 바이트를 지정한 상대 경로(S3 key, folder prefix 제외)로 업로드한다. */
    private void uploadBytes(String relativePath, byte[] bytes, String contentType) throws IOException {
        String s3Key = folder + relativePath;
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));
            log.info("[FileStorage] PutObject(썸네일) 성공: key={}, size={}bytes", s3Key, bytes.length);
        } catch (S3Exception e) {
            log.error("[FileStorage] PutObject(썸네일) 실패: key={}, {}", s3Key, e.getMessage());
            throw new IOException("S3 썸네일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 파일 읽기
     */
    public byte[] loadFile(String filePath) throws IOException {
        checkS3Enabled();

        // folder prefix 추가
        String s3Key = folder + filePath;
        log.info("[FileStorage] GetObject 요청 시작: bucket={}, key={}", bucketName, s3Key);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            byte[] content = s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
            log.info("[FileStorage] GetObject 성공: key={}, size={}bytes", s3Key, content.length);
            return content;
        } catch (NoSuchKeyException e) {
            log.warn("[FileStorage] GetObject 실패 - 파일 없음: key={}, statusCode={}", s3Key, e.statusCode());
            throw new IOException("파일을 찾을 수 없습니다: " + filePath);
        } catch (S3Exception e) {
            log.error("[FileStorage] GetObject 실패: key={}, statusCode={}, errorCode={}, message={}",
                    s3Key, e.statusCode(), e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage());
            throw new IOException("S3 파일 읽기에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 파일 삭제
     */
    public void deleteFile(String filePath) throws IOException {
        checkS3Enabled();

        try {
            // folder prefix 추가
            String s3Key = folder + filePath;

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("[FileStorage] S3 파일 삭제: s3://{}/{}", bucketName, s3Key);
        } catch (S3Exception e) {
            log.error("[FileStorage] S3 파일 삭제 실패: {}", e.getMessage());
            throw new IOException("S3 파일 삭제에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 파일 존재 여부 확인
     */
    public boolean fileExists(String filePath) {
        if (!s3Enabled) {
            log.warn("[FileStorage] HeadObject 스킵 - S3 비활성화");
            return false;
        }

        // folder prefix 추가
        String s3Key = folder + filePath;
        log.info("[FileStorage] HeadObject 요청 시작: bucket={}, key={}", bucketName, s3Key);

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headObjectRequest);
            log.info("[FileStorage] HeadObject 성공: key={}, contentLength={}, contentType={}",
                    s3Key, response.contentLength(), response.contentType());
            return true;
        } catch (NoSuchKeyException e) {
            log.warn("[FileStorage] HeadObject 실패 - 파일 없음: key={}, statusCode={}", s3Key, e.statusCode());
            return false;
        } catch (S3Exception e) {
            log.error("[FileStorage] HeadObject 실패: key={}, statusCode={}, errorCode={}, errorMessage={}",
                    s3Key, e.statusCode(),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "N/A",
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
            return false;
        }
    }

    /**
     * S3 파일 URL 생성
     */
    public String getFileUrl(String filePath) {
        if (!s3Enabled) {
            return null;
        }
        String s3Key = folder + filePath;
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, s3Key);
    }

    /**
     * S3 활성화 여부 확인
     */
    public boolean isS3Enabled() {
        return s3Enabled;
    }

    // ------------------------------------------------------------------
    // Content-Type 판정 — 실제 규칙은 FileContentTypeResolver에 있다.
    // ------------------------------------------------------------------

    /**
     * 파일 내용·이름·클라이언트 선언을 모두 보고 Content-Type을 정한다.
     * 판정 순서와 근거는 {@link FileContentTypeResolver} 주석 참고.
     */
    public String resolveContentType(byte[] content, String fileNameOrPath, String declaredContentType) {
        return FileContentTypeResolver.resolve(content, fileNameOrPath, declaredContentType);
    }

    /**
     * 업로드 파일의 앞부분만 읽어 Content-Type을 미리 알아낸다.
     *
     * <p>전체를 다시 읽지 않으므로 "이 파일이 이미지인가"를 저장 전에 싸게 판단할 수 있다.
     * 여기서 나오는 값은 {@link #storeFile}이 S3에 박는 값과 같은 규칙으로 계산된다.
     */
    public String probeContentType(MultipartFile file) {
        byte[] head = new byte[64];
        int read = 0;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            log.warn("[FileStorage] Content-Type 판정을 위한 선행 읽기 실패 - 확장자로 판정합니다: file={}, {}",
                    file.getOriginalFilename(), e.getMessage());
        }
        return resolveContentType(read > 0 ? Arrays.copyOf(head, read) : null,
                file.getOriginalFilename(), file.getContentType());
    }

    /** 확장자(또는 ".확장자")만으로 Content-Type을 정한다. */
    public String determineContentType(String extension) {
        return FileContentTypeResolver.byExtensionOrDefault(extension);
    }

    /** 파일명이나 S3 경로에서 확장자를 떼어 Content-Type을 정한다. */
    public String determineContentTypeFromPath(String pathOrFileName) {
        return FileContentTypeResolver.byPathOrDefault(pathOrFileName);
    }
}
