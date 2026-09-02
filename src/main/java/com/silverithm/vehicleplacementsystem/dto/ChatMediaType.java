package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.service.FileContentTypeResolver;

/**
 * 채팅 메시지를 화면에서 어떻게 그릴지 알려주는 <b>파생</b> 값.
 *
 * <p><b>왜 저장된 {@code type}에 VIDEO를 더하지 않았나.</b> 두 가지가 걸린다.
 * <ol>
 *   <li><b>구버전 앱이 첨부를 통째로 잃는다.</b> 앱의 {@code _parseMessageType}은 모르는 값을
 *       {@code default:} 가지에서 <b>TEXT</b>로 떨어뜨린다(FILE이 아니다). 서버가 {@code "VIDEO"}를
 *       내보내는 순간, 이미 배포된 앱에서 동영상은 파일 첨부가 아니라 <b>파일명만 적힌 맹탕 글</b>이
 *       되어 다운로드 링크 자체가 사라진다. "최소한 파일로는 보여야 한다"는 선을 정확히 넘는다.</li>
 *   <li><b>DB가 막는다.</b> {@code chat_messages.type}은 MySQL
 *       {@code ENUM('TEXT','IMAGE','FILE','SYSTEM')}이다(V1.16.1). 새 값을 쓰려면 마이그레이션이
 *       먼저 돌아야 하는데 api-server는 수동 배포라 코드가 나갔다고 스키마가 따라간다는 보장이 없다.
 *       순서가 어긋나면 동영상 업로드가 INSERT 단계에서 실패한다.</li>
 * </ol>
 *
 * <p><b>그래서 파생 필드다.</b> 저장은 그대로 {@code IMAGE}/{@code FILE}로 두고, 응답에만
 * {@code mediaType}을 얹는다. 모르는 JSON 키는 구버전 앱이 그냥 무시하므로 안전하고,
 * 읽을 때 계산하니 <b>이미 FILE로 쌓여 있는 옛 동영상들도 백필 없이 그대로 동영상으로 살아난다.</b>
 * 마이그레이션도, 배포 순서 의존도 없다.
 *
 * <p>판정 근거는 저장된 {@code mimeType}이 먼저고, 그게 비었거나 {@code application/octet-stream}인
 * 옛 메시지를 위해 파일명 확장자를 보조로 쓴다.
 */
public final class ChatMediaType {

    public static final String IMAGE = "IMAGE";
    public static final String VIDEO = "VIDEO";
    public static final String FILE = "FILE";

    private ChatMediaType() {
    }

    /**
     * 화면용 미디어 종류를 정한다.
     *
     * @param storedType 저장된 메시지 타입 이름(TEXT/IMAGE/FILE/SYSTEM)
     * @param mimeType   저장된 Content-Type. 옛 메시지는 비어 있거나 틀릴 수 있다
     * @param fileName   보여주는 파일명. mimeType이 못 미더울 때의 보조 단서
     * @return IMAGE/VIDEO/FILE 중 하나. 첨부가 아닌 메시지(TEXT·SYSTEM)면 null
     */
    public static String resolve(String storedType, String mimeType, String fileName) {
        if (!"IMAGE".equals(storedType) && !"FILE".equals(storedType)) {
            return null;
        }
        if (FileContentTypeResolver.isVideoContentType(mimeType)) {
            return VIDEO;
        }
        // mimeType이 이미 이미지라고 말하면 확장자를 더 볼 필요가 없다.
        if (mimeType != null && mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
            return IMAGE;
        }
        // 오디오라고 말하면 동영상 확장자 표를 타지 않는다(회의 녹음 webm 등).
        if (mimeType != null && mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("audio/")) {
            return FILE;
        }
        if (FileContentTypeResolver.isVideoFileName(fileName)) {
            return VIDEO;
        }
        return "IMAGE".equals(storedType) ? IMAGE : FILE;
    }
}
