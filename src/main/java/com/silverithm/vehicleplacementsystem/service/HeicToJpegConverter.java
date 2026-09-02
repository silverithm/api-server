package com.silverithm.vehicleplacementsystem.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 아이폰이 찍은 HEIC/HEIF 사진을 JPEG로 바꾼다.
 *
 * <p><b>왜 서버에서 바꾸나.</b> 크롬·엣지·파이어폭스는 HEIC를 렌더링하지 않는다(사파리만 된다).
 * 앱에 "올리기 전에 변환"을 넣었지만 스토어 배포가 모든 폰에 깔리는 데는 시간이 걸리고,
 * 업데이트를 미루는 구버전 앱은 영원히 남는다. 그동안 올라온 사진이 웹에서 안 보이면
 * 손으로 변환해 고쳐야 한다. 받는 쪽에서 막는 게 맞다.
 *
 * <p><b>왜 라이브러리가 아니라 외부 명령인가.</b> JVM에서 HEIC를 읽는 실질적 선택지인
 * {@code nightmonkeys:imageio-heif}는 JDK 22의 Foreign Linker API를 요구한다. 이 프로젝트는
 * Java 17이라 그 플러그인은 로드되자마자 스스로 등록을 해제한다(=아무 일도 일어나지 않는다).
 * 반면 {@code heif-convert}(libheif-examples)는 도커 이미지에 패키지 두 개(약 10MB)면 끝난다.
 * Dockerfile의 {@code libheif-examples}, {@code libheif-plugin-libde265} 설치와 짝을 이룬다.
 *
 * <p><b>실패는 조용히 넘긴다.</b> 변환이 안 되면 null을 돌려주고 호출 측은 원본을 그대로 쓴다.
 * 사진이 웹에서 안 보이는 것보다 사진이 아예 안 올라가는 게 나쁘다.
 */
@Component
@Slf4j
public class HeicToJpegConverter {

    /**
     * JPEG 품질. 원본 해상도를 그대로 두고 88로 인코딩하면 아이폰 사진 기준 1.3~1.7MB가 나온다.
     * 어르신 상태를 남기는 기록이라 흐려지면 쓸모가 없어지므로 크기보다 화질을 우선한다.
     */
    private static final String JPEG_QUALITY = "88";

    /** 변환이 이 시간을 넘기면 포기하고 원본을 쓴다. 업로드 요청을 무한정 붙잡지 않기 위한 상한. */
    private static final long TIMEOUT_SECONDS = 20;

    /**
     * 코덱이 쓸 스레드 수. 운영 서버가 2 vCPU라 변환 한 건이 CPU를 다 가져가면
     * 같은 순간의 다른 요청이 밀린다.
     */
    private static final String CODEC_THREADS = "2";

    /** heif-convert 실행 파일. 도커 이미지에서는 PATH에 있다. */
    @Value("${carev.image.heic-convert-command:heif-convert}")
    private String command = "heif-convert";

    /** 사용 가능 여부 캐시 — 매 업로드마다 --version을 돌리지 않는다. (null = 아직 확인 안 함) */
    private final AtomicReference<Boolean> available = new AtomicReference<>();

    /** 이 Content-Type이 HEIC/HEIF 계열인지. */
    public static boolean isHeic(String contentType) {
        if (contentType == null) {
            return false;
        }
        String value = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return value.startsWith("image/heic") || value.startsWith("image/heif");
    }

    /** 이 서버에서 heif-convert를 쓸 수 있는지. 첫 호출에서만 실제로 확인하고 결과를 기억한다. */
    public boolean isAvailable() {
        Boolean cached = available.get();
        if (cached != null) {
            return cached;
        }
        boolean probed = probe();
        available.set(probed);
        return probed;
    }

    private boolean probe() {
        try {
            Process process = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[HEIC] {} --version 이 응답하지 않습니다 - HEIC 변환을 건너뜁니다.", command);
                return false;
            }
            if (process.exitValue() != 0) {
                log.warn("[HEIC] {} 실행 실패(exit={}) - HEIC 변환을 건너뜁니다.", command, process.exitValue());
                return false;
            }
            log.info("[HEIC] 변환기 사용 가능: {}", output.lines().findFirst().orElse(command));
            return true;
        } catch (IOException e) {
            // 도커 이미지에 libheif-examples가 없으면 여기로 온다. 업로드는 계속돼야 하므로 경고만.
            log.warn("[HEIC] {} 를 찾을 수 없습니다 - HEIC는 원본 그대로 저장됩니다. ({})", command, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * HEIC/HEIF 바이트를 JPEG 바이트로 바꾼다.
     *
     * <p>해상도는 줄이지 않는다. libheif가 회전(irot/imir)을 픽셀에 적용하고 EXIF도 옮겨 주므로
     * 촬영 시각·방향이 살아남는다. 자바에서 다시 디코딩·인코딩하면 EXIF가 날아가고
     * 4080x3060 한 장에 힙 50MB를 더 쓰게 되므로 여기서는 손대지 않는다.
     *
     * @return 변환된 JPEG 바이트. 변환기가 없거나 실패하면 null (호출 측은 원본을 그대로 쓴다)
     */
    public byte[] toJpeg(byte[] heicContent) {
        if (heicContent == null || heicContent.length == 0 || !isAvailable()) {
            return null;
        }

        Path input = null;
        Path output = null;
        long startedAt = System.currentTimeMillis();
        try {
            input = Files.createTempFile("carev-heic-", ".heic");
            output = Files.createTempFile("carev-heic-", ".jpg");
            Files.write(input, heicContent);
            // heif-convert는 출력 파일이 이미 있어도 덮어쓰지만, 실패했을 때 0바이트 파일을
            // 결과로 착각하지 않도록 성공 판정에서 크기를 확인한다.

            List<String> cmd = List.of(command,
                    "--quiet",
                    "-q", JPEG_QUALITY,
                    "--codec-threads", CODEC_THREADS,
                    input.toString(),
                    output.toString());

            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String consoleOutput = new String(process.getInputStream().readAllBytes()).trim();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[HEIC] 변환 시간 초과({}초) - 원본을 그대로 저장합니다.", TIMEOUT_SECONDS);
                return null;
            }
            if (process.exitValue() != 0) {
                log.warn("[HEIC] 변환 실패(exit={}): {}", process.exitValue(), consoleOutput);
                return null;
            }

            byte[] jpeg = Files.readAllBytes(output);
            if (jpeg.length == 0) {
                log.warn("[HEIC] 변환 결과가 비어 있습니다 - 원본을 그대로 저장합니다.");
                return null;
            }

            log.info("[HEIC] 변환 완료: {}bytes -> {}bytes, {}ms",
                    heicContent.length, jpeg.length, System.currentTimeMillis() - startedAt);
            return jpeg;
        } catch (IOException e) {
            log.warn("[HEIC] 변환 중 오류 - 원본을 그대로 저장합니다: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
        }
    }

    /** 파일명의 확장자를 .jpg로 바꾼다. 확장자가 없으면 붙인다. */
    public static String toJpegFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "photo.jpg";
        }
        int dotIndex = fileName.lastIndexOf('.');
        int slashIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (dotIndex > 0 && dotIndex > slashIndex) {
            return fileName.substring(0, dotIndex) + ".jpg";
        }
        return fileName + ".jpg";
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[HEIC] 임시 파일 삭제 실패: {}", path);
        }
    }
}
