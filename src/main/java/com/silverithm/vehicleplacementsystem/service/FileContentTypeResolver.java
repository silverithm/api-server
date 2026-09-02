package com.silverithm.vehicleplacementsystem.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 업로드된 파일의 Content-Type을 정한다.
 *
 * <p><b>왜 이게 중요한가.</b> 채팅 사진은 S3 객체 URL을 브라우저·앱이 직접 연다.
 * 그때 사진으로 보이느냐 마느냐를 오직 S3에 박아둔 Content-Type이 결정한다.
 * 여기서 {@code application/octet-stream}이 나가면 원본이 아예 안 보인다.
 * 그래서 이 클래스의 합격선은 <b>"이미지는 어떻게든 image/*로 나가게 한다"</b>이다.
 *
 * <p><b>판정 순서와 그 이유.</b>
 * <ol>
 *   <li><b>매직 넘버(파일 실제 내용)</b> — 가장 믿을 수 있다. 확장자와 클라이언트가 보낸 MIME은
 *       둘 다 사람·앱이 붙이는 이름표라서 틀리거나 거짓말을 한다. 카톡·스캔 앱을 거치면
 *       {@code .jpg}인데 실제로는 PNG나 HEIC인 파일이 흔하다. 파일의 첫 바이트는 파일 자신이다.</li>
 *   <li><b>확장자 매핑</b> — 매직 넘버 표에 없는 포맷(hwp, docx 등 컨테이너류 포함)을 받는다.</li>
 *   <li><b>클라이언트가 선언한 MIME</b> — 앞의 둘이 모르는 새 이미지 포맷을 위한 안전망.
 *       단 클라이언트 입력은 조작 가능하므로 {@code image/*}이면서 svg/xml 계열이 아닐 때만 받는다.</li>
 *   <li>그래도 모르면 {@code application/octet-stream}.</li>
 * </ol>
 *
 * <p><b>svg는 어느 경로로도 image로 선언하지 않는다.</b> SVG는 스크립트를 품을 수 있는 XML이라
 * {@code image/svg+xml}로 S3에서 그대로 열리면 저장형 XSS 통로가 된다. 사진 문제를 푸는 데
 * svg가 필요하지도 않다.
 */
public final class FileContentTypeResolver {

    public static final String OCTET_STREAM = "application/octet-stream";

    /** 매직 넘버를 확인하기 위해 읽는 앞부분 바이트 수. ISO-BMFF 호환 브랜드까지 훑기에 충분한 길이. */
    private static final int MAGIC_HEAD_LENGTH = 64;

    private FileContentTypeResolver() {
    }

    // ------------------------------------------------------------------
    // 확장자 → Content-Type
    // ------------------------------------------------------------------

    /**
     * 확장자로 판정하는 표.
     * 이미지는 "현장에서 실제로 올라오는 것"을 기준으로 넉넉히 담았다.
     * 표에 없더라도 매직 넘버나 클라이언트 MIME으로 구제되므로, 이 표는 마지막 방어선이 아니라 지름길이다.
     */
    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE = Map.ofEntries(
            // --- 이미지 : JPEG 계열 ---
            // jfif/jpe/jif는 윈도우·스캔 프로그램이 저장할 때 종종 붙이는 JPEG의 다른 이름이다.
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("jfif", "image/jpeg"),
            Map.entry("jpe", "image/jpeg"),
            Map.entry("jif", "image/jpeg"),

            // --- 이미지 : 아이폰 기본 포맷 ---
            // 캐논 카메라는 HEIF를 .hif로 저장한다.
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("hif", "image/heif"),
            Map.entry("heics", "image/heic-sequence"),
            Map.entry("heifs", "image/heif-sequence"),

            // --- 이미지 : 그 밖에 실제로 들어오는 것들 ---
            Map.entry("png", "image/png"),
            Map.entry("apng", "image/apng"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),   // 안드로이드·웹 저장본. 프로필 사진 허용 목록에 이미 있었다.
            Map.entry("avif", "image/avif"),
            Map.entry("bmp", "image/bmp"),     // 윈도우 그림판·화면 캡처
            Map.entry("dib", "image/bmp"),
            Map.entry("tif", "image/tiff"),    // 스캐너 기본 출력
            Map.entry("tiff", "image/tiff"),
            Map.entry("jxl", "image/jxl"),
            Map.entry("ico", "image/x-icon"),

            // --- 문서 ---
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("hwp", "application/x-hwp"),
            Map.entry("hwpx", "application/hwp+zip"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("zip", "application/zip"),

            // --- 회의 녹음 : FileController.isAllowedAudioExtension()과 짝을 맞춘다 ---
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("wav", "audio/wav"),
            Map.entry("aac", "audio/aac"),
            Map.entry("ogg", "audio/ogg"),
            // 이 프로젝트에서 webm은 회의 녹음(MediaRecorder 오디오)으로만 올라온다.
            Map.entry("webm", "audio/webm")
    );

    /** 업로드를 허용하는 이미지 확장자 — 위 표에서 image/*로 매핑되는 것들. */
    public static final Set<String> IMAGE_EXTENSIONS = EXTENSION_TO_CONTENT_TYPE.entrySet().stream()
            .filter(e -> e.getValue().startsWith("image/"))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    // ------------------------------------------------------------------
    // 공개 API
    // ------------------------------------------------------------------

    /**
     * 파일 내용·이름·클라이언트 선언을 모두 보고 Content-Type을 정한다. 클래스 주석의 4단계 순서를 따른다.
     *
     * @param content               파일 내용(앞부분만 봐도 된다). null이면 매직 넘버 단계를 건너뛴다.
     * @param fileNameOrPath        원본 파일명 또는 S3 경로. 확장자를 여기서 뽑는다.
     * @param declaredContentType   클라이언트가 보낸 Content-Type. 신뢰하지 않고 마지막 안전망으로만 쓴다.
     */
    public static String resolve(byte[] content, String fileNameOrPath, String declaredContentType) {
        // 1. 파일 자신의 내용
        String sniffed = detectByMagic(content);
        if (sniffed != null) {
            return sniffed;
        }

        // 2. 확장자
        String byExtension = byExtension(extractExtension(fileNameOrPath));
        if (byExtension != null) {
            return byExtension;
        }

        // 3. 클라이언트가 선언한 MIME (image/*만, svg 제외)
        String declared = acceptableDeclaredImageType(declaredContentType);
        if (declared != null) {
            return declared;
        }

        return OCTET_STREAM;
    }

    /** 확장자(또는 ".확장자")만으로 판정한다. 모르면 {@code application/octet-stream}. */
    public static String byExtensionOrDefault(String extension) {
        String contentType = byExtension(normalizeExtension(extension));
        return contentType != null ? contentType : OCTET_STREAM;
    }

    /** 파일명·경로에서 확장자를 뽑아 판정한다. 모르면 {@code application/octet-stream}. */
    public static String byPathOrDefault(String fileNameOrPath) {
        String contentType = byExtension(extractExtension(fileNameOrPath));
        return contentType != null ? contentType : OCTET_STREAM;
    }

    /** 해당 확장자가 우리가 아는 이미지인지. */
    public static boolean isImageExtension(String extension) {
        String normalized = normalizeExtension(extension);
        return normalized != null && IMAGE_EXTENSIONS.contains(normalized);
    }

    /** 파일 내용의 매직 넘버가 이미지인지. 내용으로 판정되지 않으면 false. */
    public static boolean looksLikeImage(byte[] content) {
        String sniffed = detectByMagic(content);
        return sniffed != null && sniffed.startsWith("image/");
    }

    // ------------------------------------------------------------------
    // 1단계 : 매직 넘버
    // ------------------------------------------------------------------

    /**
     * 파일 앞부분의 시그니처로 포맷을 알아낸다. 모르면 null.
     *
     * <p>여기서 돌려주는 값은 고정된 표 안에서만 나오므로, 확장자를 위조해도
     * {@code text/html}이나 {@code image/svg+xml} 같은 실행 가능한 타입으로 승격되지 않는다.
     */
    public static String detectByMagic(byte[] content) {
        if (content == null || content.length < 4) {
            return null;
        }
        byte[] head = content.length <= MAGIC_HEAD_LENGTH
                ? content
                : java.util.Arrays.copyOf(content, MAGIC_HEAD_LENGTH);

        // --- 이미지 ---
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (matchesAscii(head, 0, "GIF87a") || matchesAscii(head, 0, "GIF89a")) {
            return "image/gif";
        }
        if (matchesAscii(head, 0, "BM")) {
            return "image/bmp";
        }
        if (startsWith(head, 0x49, 0x49, 0x2A, 0x00) || startsWith(head, 0x4D, 0x4D, 0x00, 0x2A)) {
            return "image/tiff";
        }
        if (matchesAscii(head, 0, "RIFF") && matchesAscii(head, 8, "WEBP")) {
            return "image/webp";
        }
        if (startsWith(head, 0x00, 0x00, 0x01, 0x00)) {
            return "image/x-icon";
        }
        if (startsWith(head, 0xFF, 0x0A)
                || startsWith(head, 0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20)) {
            return "image/jxl";
        }

        // --- ISO-BMFF 계열 : HEIC/HEIF/AVIF와 mp4/m4a가 같은 상자에 들어 있다 ---
        if (matchesAscii(head, 4, "ftyp")) {
            String isoBmff = detectIsoBmff(head);
            if (isoBmff != null) {
                return isoBmff;
            }
        }

        // --- 문서 ---
        if (matchesAscii(head, 0, "%PDF-")) {
            return "application/pdf";
        }

        // --- 오디오 (회의 녹음) ---
        if (matchesAscii(head, 0, "RIFF") && matchesAscii(head, 8, "WAVE")) {
            return "audio/wav";
        }
        if (matchesAscii(head, 0, "OggS")) {
            return "audio/ogg";
        }
        if (matchesAscii(head, 0, "ID3") || startsWith(head, 0xFF, 0xFB) || startsWith(head, 0xFF, 0xF3)
                || startsWith(head, 0xFF, 0xF2)) {
            return "audio/mpeg";
        }
        if (startsWith(head, 0x1A, 0x45, 0xDF, 0xA3)) {
            // Matroska/WebM 컨테이너. 이 프로젝트에서는 회의 녹음으로만 올라온다.
            return "audio/webm";
        }

        return null;
    }

    /**
     * ISO Base Media File Format(ftyp 상자)의 브랜드를 읽는다.
     *
     * <p>major brand만 보면 놓친다. 아이폰은 {@code heic}를, 안드로이드·일부 카메라는 major를
     * {@code mif1}로 두고 호환 브랜드에 {@code heic}를 넣는 식으로 제각각이라
     * major brand(offset 8)를 먼저 보고, 모르면 호환 브랜드 목록(offset 16~)까지 훑는다.
     */
    private static String detectIsoBmff(byte[] head) {
        String major = readAscii(head, 8, 4);
        String byMajor = brandToContentType(major);
        if (byMajor != null) {
            return byMajor;
        }

        // 호환 브랜드는 offset 16부터 4바이트씩 나열된다. ftyp 상자 크기를 넘지 않는 선까지만 본다.
        long boxSize = readUint32(head, 0);
        int limit = (int) Math.min(head.length, boxSize > 0 ? boxSize : head.length);
        for (int offset = 16; offset + 4 <= limit; offset += 4) {
            String compatible = brandToContentType(readAscii(head, offset, 4));
            if (compatible != null) {
                return compatible;
            }
        }
        return null;
    }

    private static String brandToContentType(String brand) {
        if (brand == null) {
            return null;
        }
        return switch (brand.toLowerCase(Locale.ROOT)) {
            // 정지 이미지
            case "heic", "heix", "hevc", "hevx" -> "image/heic";
            case "mif1", "mif2" -> "image/heif";
            case "avif" -> "image/avif";
            // 연속 촬영/시퀀스
            case "heim", "heis", "hevm", "hevs" -> "image/heic-sequence";
            case "msf1" -> "image/heif-sequence";
            case "avis" -> "image/avif-sequence";
            // 같은 컨테이너를 쓰는 동영상·오디오 (회의 녹음이 여기로 온다)
            case "m4a " -> "audio/mp4";
            case "m4b " -> "audio/mp4";
            case "isom", "iso2", "mp41", "mp42", "avc1", "m4v " -> "video/mp4";
            case "qt  " -> "video/quicktime";
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // 2단계 : 확장자
    // ------------------------------------------------------------------

    private static String byExtension(String normalizedExtension) {
        if (normalizedExtension == null) {
            return null;
        }
        return EXTENSION_TO_CONTENT_TYPE.get(normalizedExtension);
    }

    /** 앞의 점을 떼고 소문자로. 비어 있으면 null. */
    private static String normalizeExtension(String extension) {
        if (extension == null) {
            return null;
        }
        String trimmed = extension.trim();
        if (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /** 파일명·경로에서 확장자만 뽑는다. "chat.v2/12/nofile"처럼 디렉터리의 점은 확장자가 아니다. */
    public static String extractExtension(String fileNameOrPath) {
        if (fileNameOrPath == null) {
            return null;
        }
        int dotIndex = fileNameOrPath.lastIndexOf('.');
        int slashIndex = fileNameOrPath.lastIndexOf('/');
        if (dotIndex < 0 || dotIndex < slashIndex) {
            return null;
        }
        return normalizeExtension(fileNameOrPath.substring(dotIndex + 1));
    }

    // ------------------------------------------------------------------
    // 3단계 : 클라이언트가 선언한 MIME
    // ------------------------------------------------------------------

    /**
     * 클라이언트가 보낸 Content-Type을 받아줄지 판단한다.
     *
     * <p>이 값은 업로더가 마음대로 정할 수 있으므로 그대로 S3에 박으면 위험하다.
     * 그래서 (1) {@code image/}로 시작하고 (2) svg·xml 계열이 아니고 (3) 토큰 문법에 맞을 때만 받는다.
     * 우리가 모르는 이미지 포맷이 나와도 사진이 안 보이는 일이 없게 하려는 안전망이다.
     * 모르는 {@code image/*}는 브라우저가 그냥 못 그릴 뿐 실행되지 않는다.
     */
    private static String acceptableDeclaredImageType(String declaredContentType) {
        if (declaredContentType == null) {
            return null;
        }
        // "image/jpeg; charset=..." 같은 파라미터는 떼어낸다.
        String value = declaredContentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith("image/")) {
            return null;
        }
        String subtype = value.substring("image/".length());
        if (subtype.isEmpty() || subtype.contains("svg") || subtype.contains("xml")) {
            return null;
        }
        if (!subtype.matches("[a-z0-9][a-z0-9!#$&^_.+-]{0,60}")) {
            return null;
        }
        return value;
    }

    // ------------------------------------------------------------------
    // 바이트 비교 도우미
    // ------------------------------------------------------------------

    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAscii(byte[] head, int offset, String expected) {
        if (offset < 0 || offset + expected.length() > head.length) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if ((head[offset + i] & 0xFF) != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String readAscii(byte[] head, int offset, int length) {
        if (offset < 0 || offset + length > head.length) {
            return null;
        }
        return new String(head, offset, length, StandardCharsets.ISO_8859_1);
    }

    private static long readUint32(byte[] head, int offset) {
        if (offset + 4 > head.length) {
            return -1;
        }
        return ((long) (head[offset] & 0xFF) << 24)
                | ((head[offset + 1] & 0xFF) << 16)
                | ((head[offset + 2] & 0xFF) << 8)
                | (head[offset + 3] & 0xFF);
    }
}
