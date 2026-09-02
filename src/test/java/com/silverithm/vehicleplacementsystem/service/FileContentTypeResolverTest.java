package com.silverithm.vehicleplacementsystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Content-Type 판정 규칙을 확장자별로 못박아 둔다.
 * 새 포맷이 빠지면 여기서 걸리도록 기대값을 표로 적어 둔 것이 이 테스트의 목적이다.
 */
@DisplayName("파일 Content-Type 판정")
class FileContentTypeResolverTest {

    @Nested
    @DisplayName("확장자별 기대 Content-Type")
    class ByExtension {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                // 아이폰 기본 사진 포맷 — 이게 빠져 사진이 안 올라가고 안 보였다
                "heic, image/heic",
                "HEIC, image/heic",
                ".heic, image/heic",
                "heif, image/heif",
                "hif, image/heif",
                "heics, image/heic-sequence",
                "heifs, image/heif-sequence",

                // JPEG의 여러 이름
                "jpg, image/jpeg",
                "jpeg, image/jpeg",
                "jfif, image/jpeg",
                "jpe, image/jpeg",
                "jif, image/jpeg",

                // 그 밖에 현장에서 실제로 올라오는 것들
                "png, image/png",
                "apng, image/apng",
                "gif, image/gif",
                "webp, image/webp",
                "avif, image/avif",
                "bmp, image/bmp",
                "dib, image/bmp",
                "tif, image/tiff",
                "tiff, image/tiff",
                "jxl, image/jxl",
                "ico, image/x-icon",

                // 문서 — 기존 동작이 유지되는지
                "pdf, application/pdf",
                "hwp, application/x-hwp",
                "hwpx, application/hwp+zip",
                "doc, application/msword",
                "docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "xls, application/vnd.ms-excel",
                "xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "ppt, application/vnd.ms-powerpoint",
                "pptx, application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "txt, text/plain",
                "csv, text/csv",
                "zip, application/zip",

                // 회의 녹음
                "mp3, audio/mpeg",
                "m4a, audio/mp4",
                "wav, audio/wav",
                "aac, audio/aac",
                "ogg, audio/ogg",
                "webm, audio/webm",
        })
        void mapsExtensionToContentType(String extension, String expected) {
            assertEquals(expected, FileContentTypeResolver.byExtensionOrDefault(extension));
        }

        @ParameterizedTest(name = "{0}는 이미지로 선언하지 않는다")
        @CsvSource({"svg", "svgz", "html", "htm", "xml", "js", "exe", "sh"})
        void doesNotDeclareScriptableTypesAsImage(String extension) {
            // svg는 스크립트를 품을 수 있는 XML이라 S3에서 그대로 열리면 저장형 XSS가 된다.
            assertEquals("application/octet-stream", FileContentTypeResolver.byExtensionOrDefault(extension));
            assertFalse(FileContentTypeResolver.isImageExtension(extension));
        }

        @Test
        @DisplayName("빈 값·null은 octet-stream")
        void fallsBackForBlank() {
            assertEquals("application/octet-stream", FileContentTypeResolver.byExtensionOrDefault(null));
            assertEquals("application/octet-stream", FileContentTypeResolver.byExtensionOrDefault(""));
            assertEquals("application/octet-stream", FileContentTypeResolver.byExtensionOrDefault("."));
        }

        @Test
        @DisplayName("경로에서 확장자를 뽑을 때 디렉터리의 점에 속지 않는다")
        void extractsExtensionFromPath() {
            assertEquals("image/heic", FileContentTypeResolver.byPathOrDefault("chat/12/9f1c-uuid.heic"));
            assertEquals("image/jpeg", FileContentTypeResolver.byPathOrDefault("chat/12/uuid_thumb.jpg"));
            assertEquals("application/pdf", FileContentTypeResolver.byPathOrDefault("approvals/a.b.c.pdf"));
            assertEquals("application/octet-stream", FileContentTypeResolver.byPathOrDefault("chat.v2/12/nofile"));
            assertEquals("application/octet-stream", FileContentTypeResolver.byPathOrDefault("noextension"));
            assertEquals("application/octet-stream", FileContentTypeResolver.byPathOrDefault(null));
        }

        @Test
        @DisplayName("이미지 확장자 목록과 Content-Type 표가 어긋나지 않는다")
        void imageExtensionSetMatchesTable() {
            assertTrue(FileContentTypeResolver.isImageExtension("heic"));
            assertTrue(FileContentTypeResolver.isImageExtension(".HEIC"));
            assertTrue(FileContentTypeResolver.isImageExtension("webp"));
            assertFalse(FileContentTypeResolver.isImageExtension("pdf"));
            assertFalse(FileContentTypeResolver.isImageExtension("mp3"));
            for (String extension : FileContentTypeResolver.IMAGE_EXTENSIONS) {
                assertTrue(FileContentTypeResolver.byExtensionOrDefault(extension).startsWith("image/"),
                        extension + "는 이미지 목록에 있는데 image/*로 매핑되지 않습니다");
            }
        }
    }

    @Nested
    @DisplayName("파일 실제 내용(매직 넘버)으로 판정")
    class ByMagicNumber {

        /** 자바가 실제로 인코딩할 수 있는 포맷은 진짜 파일을 만들어 넣는다. */
        static Stream<Arguments> realImageFiles() throws Exception {
            return Stream.of(
                    Arguments.of("jpg", "image/jpeg", realImage("jpg")),
                    Arguments.of("png", "image/png", realImage("png")),
                    Arguments.of("gif", "image/gif", realImage("gif")),
                    Arguments.of("bmp", "image/bmp", realImage("bmp")),
                    Arguments.of("tiff", "image/tiff", realImage("tiff"))
            );
        }

        @ParameterizedTest(name = "실제 {0} 파일 -> {1}")
        @MethodSource("realImageFiles")
        void detectsRealImageFiles(String format, String expected, byte[] bytes) {
            assertEquals(expected, FileContentTypeResolver.detectByMagic(bytes));
            assertTrue(FileContentTypeResolver.looksLikeImage(bytes));
        }

        @Test
        @DisplayName("아이폰 HEIC(ftypheic)를 알아본다")
        void detectsHeic() {
            assertEquals("image/heic", FileContentTypeResolver.detectByMagic(isoBmff("heic")));
            assertEquals("image/heic", FileContentTypeResolver.detectByMagic(isoBmff("heix")));
            assertEquals("image/heic-sequence", FileContentTypeResolver.detectByMagic(isoBmff("hevs")));
        }

        @Test
        @DisplayName("major brand가 mif1이고 호환 브랜드에 heic가 있는 파일도 알아본다")
        void detectsHeicViaCompatibleBrand() {
            // 안드로이드·일부 카메라가 이렇게 저장한다. major brand만 보면 놓친다.
            assertEquals("image/heif", FileContentTypeResolver.detectByMagic(isoBmff("mif1", "mif1", "heic")));

            // major brand를 우리가 모르는 값으로 두고 호환 브랜드에만 avif가 있는 경우
            assertEquals("image/avif", FileContentTypeResolver.detectByMagic(isoBmff("XXXX", "YYYY", "avif")));
        }

        @Test
        @DisplayName("AVIF·WebP·ICO·JPEG XL을 알아본다")
        void detectsOtherModernFormats() {
            assertEquals("image/avif", FileContentTypeResolver.detectByMagic(isoBmff("avif")));
            assertEquals("image/webp", FileContentTypeResolver.detectByMagic(riff("WEBP")));
            assertEquals("image/x-icon",
                    FileContentTypeResolver.detectByMagic(new byte[]{0, 0, 1, 0, 1, 0, 16, 16}));
            assertEquals("image/jxl",
                    FileContentTypeResolver.detectByMagic(new byte[]{(byte) 0xFF, 0x0A, 0, 0, 0, 0, 0, 0}));
        }

        @Test
        @DisplayName("PDF와 회의 녹음 포맷도 내용으로 알아본다")
        void detectsDocumentsAndAudio() {
            assertEquals("application/pdf", FileContentTypeResolver.detectByMagic(ascii("%PDF-1.7 ...")));
            assertEquals("audio/wav", FileContentTypeResolver.detectByMagic(riff("WAVE")));
            assertEquals("audio/ogg", FileContentTypeResolver.detectByMagic(ascii("OggS____")));
            assertEquals("audio/mpeg", FileContentTypeResolver.detectByMagic(ascii("ID3____")));
            assertEquals("audio/webm",
                    FileContentTypeResolver.detectByMagic(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4}));
            assertEquals("audio/mp4", FileContentTypeResolver.detectByMagic(isoBmff("M4A ")));
        }

        @Test
        @DisplayName("모르는 내용이면 null - 확장자 단계로 넘긴다")
        void returnsNullForUnknownContent() {
            assertNull(FileContentTypeResolver.detectByMagic("그냥 텍스트입니다".getBytes(StandardCharsets.UTF_8)));
            assertNull(FileContentTypeResolver.detectByMagic(new byte[0]));
            assertNull(FileContentTypeResolver.detectByMagic(null));
            assertFalse(FileContentTypeResolver.looksLikeImage("아무것도 아님".getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Nested
    @DisplayName("확장자와 실제 내용이 다를 때")
    class WhenExtensionLies {

        @Test
        @DisplayName("내용을 믿는다 - 카톡을 거쳐 .jpg가 된 PNG 사진")
        void trustsContentOverExtension() throws Exception {
            byte[] actuallyPng = realImage("png");
            assertEquals("image/png",
                    FileContentTypeResolver.resolve(actuallyPng, "KakaoTalk_20260902.jpg", "image/jpeg"));
        }

        @Test
        @DisplayName("내용을 믿는다 - 구버전 앱이 octet-stream으로 보낸 HEIC")
        void trustsContentWhenClientLiesAboutMime() {
            assertEquals("image/heic",
                    FileContentTypeResolver.resolve(isoBmff("heic"), "IMG_0001.HEIC", "application/octet-stream"));
            // 확장자마저 없는 경우에도 내용으로 구제된다
            assertEquals("image/heic", FileContentTypeResolver.resolve(isoBmff("heic"), "IMG_0001", null));
        }

        @Test
        @DisplayName("내용이 이미지가 아니면 이미지로 승격되지 않는다 - .jpg로 위장한 PDF")
        void doesNotPromoteNonImageContent() {
            assertEquals("application/pdf",
                    FileContentTypeResolver.resolve(ascii("%PDF-1.4 payload"), "photo.jpg", "image/jpeg"));
        }

        @Test
        @DisplayName("내용이 svg(XML)여도 이미지로 선언하지 않는다")
        void neverDeclaresSvgAsImage() {
            byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                    .getBytes(StandardCharsets.UTF_8);
            // 확장자·클라이언트 MIME 모두 svg를 주장해도 통과시키지 않는다
            assertEquals("application/octet-stream",
                    FileContentTypeResolver.resolve(svg, "logo.svg", "image/svg+xml"));
            assertEquals("application/octet-stream",
                    FileContentTypeResolver.resolve(svg, "logo.png.svg", "image/svg+xml"));
        }
    }

    @Nested
    @DisplayName("클라이언트가 선언한 MIME은 마지막 안전망으로만 쓴다")
    class DeclaredMimeFallback {

        @Test
        @DisplayName("우리가 모르는 새 이미지 포맷도 사진으로 나가게 해준다")
        void acceptsUnknownImageSubtype() {
            byte[] unknown = "알 수 없는 새 포맷".getBytes(StandardCharsets.UTF_8);
            assertEquals("image/vnd.future-format",
                    FileContentTypeResolver.resolve(unknown, "photo.futurefmt", "image/vnd.future-format"));
            // 파라미터는 떼고 소문자로 정규화한다
            assertEquals("image/heic",
                    FileContentTypeResolver.resolve(unknown, "photo.unknownext", "IMAGE/HEIC; charset=binary"));
        }

        @Test
        @DisplayName("이미지가 아닌 선언은 받지 않는다 - 조작 가능한 값이기 때문")
        void rejectsNonImageDeclarations() {
            byte[] unknown = "무엇인지 모를 내용".getBytes(StandardCharsets.UTF_8);
            assertEquals("application/octet-stream",
                    FileContentTypeResolver.resolve(unknown, "x.unknownext", "text/html"));
            assertEquals("application/octet-stream",
                    FileContentTypeResolver.resolve(unknown, "x.unknownext", "application/javascript"));
            assertEquals("application/octet-stream",
                    FileContentTypeResolver.resolve(unknown, "x.unknownext", "image/svg+xml"));
            assertEquals("application/octet-stream",
                    FileContentTypeResolver.resolve(unknown, "x.unknownext", "image/"));
            // 헤더 주입 시도 — 토큰 문법에 맞지 않으므로 거부된다
            assertEquals("application/octet-stream",
                    FileContentTypeResolver.resolve(unknown, "x.unknownext", "image/png\r\nX-Evil: 1"));
        }

        @Test
        @DisplayName("확장자가 먼저다 - 선언보다 확장자 매핑을 우선한다")
        void extensionBeatsDeclaration() {
            byte[] unknown = "내용은 모름".getBytes(StandardCharsets.UTF_8);
            assertEquals("image/heic", FileContentTypeResolver.resolve(unknown, "IMG_1.heic", "image/whatever"));
        }
    }

    // ------------------------------------------------------------------
    // 테스트용 파일 만들기
    // ------------------------------------------------------------------

    /** ImageIO로 실제 이미지 파일 바이트를 만든다. */
    private static byte[] realImage(String format) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 8, 8);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) {
            throw new IllegalStateException("이 JVM에 " + format + " writer가 없습니다");
        }
        return out.toByteArray();
    }

    /**
     * HEIC/AVIF/mp4가 쓰는 ISO-BMFF 컨테이너의 ftyp 상자를 만든다.
     * [상자크기(4)][ftyp(4)][major brand(4)][minor version(4)][호환 브랜드...]
     */
    private static byte[] isoBmff(String majorBrand, String... compatibleBrands) {
        int size = 16 + compatibleBrands.length * 4;
        byte[] bytes = new byte[Math.max(size, 32)];
        bytes[0] = (byte) (size >> 24);
        bytes[1] = (byte) (size >> 16);
        bytes[2] = (byte) (size >> 8);
        bytes[3] = (byte) size;
        writeAscii(bytes, 4, "ftyp");
        writeAscii(bytes, 8, majorBrand);
        for (int i = 0; i < compatibleBrands.length; i++) {
            writeAscii(bytes, 16 + i * 4, compatibleBrands[i]);
        }
        return bytes;
    }

    /** WebP/WAV가 쓰는 RIFF 컨테이너: [RIFF][크기(4)][형식(4)] */
    private static byte[] riff(String form) {
        byte[] bytes = new byte[32];
        writeAscii(bytes, 0, "RIFF");
        writeAscii(bytes, 8, form);
        return bytes;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void writeAscii(byte[] target, int offset, String value) {
        byte[] source = ascii(value);
        System.arraycopy(source, 0, target, offset, Math.min(source.length, target.length - offset));
    }
}
