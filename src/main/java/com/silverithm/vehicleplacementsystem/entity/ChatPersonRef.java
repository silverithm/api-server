package com.silverithm.vehicleplacementsystem.entity;

/**
 * 채팅 식별자 문자열("12" / "admin_12")을 표별 참조 칼럼(member_id / app_user_id)으로 옮기는 규칙.
 *
 * 채팅 표들은 오랫동안 사람을 문자열 하나로 가리켰다. 다형 외래키를 문자열에 인코딩한 꼴이라
 * DB가 무결성을 지켜주지 못했고(대응하는 직원이 없는 행이 실제로 쌓였다), 숫자가 겹치면
 * 누구인지 단정할 수도 없었다. V1.66부터 표마다 타입별 칼럼을 두고 둘 중 하나만 채운다.
 *
 * 지금은 두 표기를 함께 쓴다 — 문자열은 그대로 두고(조회는 아직 이쪽) 새 칼럼도 같이 채운다.
 * 그래서 배포해도 동작이 바뀌지 않고, 문제가 생기면 되돌리기도 쉽다.
 *
 * 사람을 가리키지 않는 값(시스템 메시지의 "system" 등)은 둘 다 null이 된다.
 */
public record ChatPersonRef(Long memberId, Long appUserId) {

    private static final ChatPersonRef NONE = new ChatPersonRef(null, null);

    public static ChatPersonRef of(String chatUserId) {
        if (chatUserId == null || chatUserId.isBlank()) {
            return NONE;
        }

        String value = chatUserId.trim();
        if (value.startsWith(ChatService_ADMIN_PREFIX)) {
            Long id = parse(value.substring(ChatService_ADMIN_PREFIX.length()));
            return id == null ? NONE : new ChatPersonRef(null, id);
        }

        Long id = parse(value);
        return id == null ? NONE : new ChatPersonRef(id, null);
    }

    /**
     * ChatService.ADMIN_ID_PREFIX와 같은 값.
     * 엔티티가 서비스를 참조하지 않도록 여기에 따로 둔다 — 두 값이 갈라지면 안 된다.
     */
    private static final String ChatService_ADMIN_PREFIX = "admin_";

    /** ADMIN / MEMBER / null(사람이 아닌 값). 결재선의 approverType과 같은 표기 */
    public String type() {
        if (appUserId != null) {
            return "ADMIN";
        }
        return memberId != null ? "MEMBER" : null;
    }

    /** 사람의 원시 id (app_user.id 또는 members.id) */
    public Long refId() {
        return appUserId != null ? appUserId : memberId;
    }

    /**
     * 예전부터 쓰던 문자열 표기("12" / "admin_12").
     * 클라이언트가 아직 이 값으로 '내 것인지'를 판단하므로 계속 함께 내려준다.
     * 저장된 칼럼이 아니라 참조에서 만들어낸 값이다.
     */
    public String legacyId() {
        if (appUserId != null) {
            return ChatService_ADMIN_PREFIX + appUserId;
        }
        return memberId != null ? String.valueOf(memberId) : null;
    }

    public static ChatPersonRef of(Long memberId, Long appUserId) {
        return new ChatPersonRef(memberId, appUserId);
    }

    private static Long parse(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
