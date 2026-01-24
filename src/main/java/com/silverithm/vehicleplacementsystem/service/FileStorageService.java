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
import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${cloud.aws.s3.folder}")
    private String folder;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            this.s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
            log.info("[FileStorage] S3 클라이언트 초기화 완료: bucket={}, folder={}, region={}", bucketName, folder, region);
        } catch (Exception e) {
            log.error("[FileStorage] S3 클라이언트 초기화 실패", e);
            throw new RuntimeException("S3 클라이언트를 초기화할 수 없습니다.", e);
        }
    }

    /**
     * 파일 저장
     * @param file 업로드할 파일
     * @param subDirectory 하위 디렉토리 (예: "templates", "attachments")
     * @return 저장된 파일 경로 (S3 key, folder prefix 제외)
     */
    public String storeFile(MultipartFile file, String subDirectory) throws IOException {
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

        try {
            // Content-Type 결정
            String contentType = determineContentType(extension);

            // S3에 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

            log.info("[FileStorage] S3 파일 업로드 완료: {} -> s3://{}/{}", originalFileName, bucketName, s3Key);

            // folder prefix를 제외한 상대 경로 반환
            return relativePath;
        } catch (S3Exception e) {
            log.error("[FileStorage] S3 업로드 실패: {}", e.getMessage());
            throw new IOException("S3 파일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 파일 읽기
     */
    public byte[] loadFile(String filePath) throws IOException {
        try {
            // folder prefix 추가
            String s3Key = folder + filePath;

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
        } catch (NoSuchKeyException e) {
            log.warn("[FileStorage] S3 파일을 찾을 수 없음: {}", filePath);
            throw new IOException("파일을 찾을 수 없습니다: " + filePath);
        } catch (S3Exception e) {
            log.error("[FileStorage] S3 파일 읽기 실패: {}", e.getMessage());
            throw new IOException("S3 파일 읽기에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 파일 삭제
     */
    public void deleteFile(String filePath) throws IOException {
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
        try {
            // folder prefix 추가
            String s3Key = folder + filePath;

            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("[FileStorage] S3 파일 존재 확인 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * S3 파일 URL 생성
     */
    public String getFileUrl(String filePath) {
        String s3Key = folder + filePath;
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, s3Key);
    }

    private String determineContentType(String extension) {
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        return switch (extension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "hwp" -> "application/x-hwp";
            case "hwpx" -> "application/hwp+zip";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}
