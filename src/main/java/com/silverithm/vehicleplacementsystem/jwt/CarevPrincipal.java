package com.silverithm.vehicleplacementsystem.jwt;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * 토큰에서 꺼낸 로그인 주체.
 *
 * 예전에는 토큰이 이름(관리자=이메일, 직원=username)과 권한만 담아서, "이 사람이 관리자 계정인지
 * 직원인지"를 알려면 매번 DB를 두 번 뒤져야 했다. 그래서 클라이언트가 보낸 id를 그냥 믿는
 * 코드가 생겼고, 채팅 식별자 규약이 바뀌자 앱이 통째로 어긋났다.
 *
 * 이제 토큰이 유형(ADMIN/MEMBER)과 id를 직접 들고 다닌다.
 * {@link org.springframework.security.core.userdetails.User}를 그대로 상속하므로
 * getUsername()을 쓰던 기존 코드는 손대지 않아도 된다.
 *
 * 주의: 이 클레임이 없는 **예전에 발급된 토큰**이 아직 살아 있다. 그런 토큰에서는
 * principalType/principalId가 null이고, 받는 쪽이 DB 조회로 메워야 한다.
 * (안 그러면 재로그인 전까지 모두가 채팅을 못 쓴다)
 */
public class CarevPrincipal extends User {

    public static final String TYPE_ADMIN = "ADMIN";
    public static final String TYPE_MEMBER = "MEMBER";

    private final String principalType;
    private final Long principalId;

    public CarevPrincipal(String username,
                          Collection<? extends GrantedAuthority> authorities,
                          String principalType,
                          Long principalId) {
        super(username, "", authorities);
        this.principalType = principalType;
        this.principalId = principalId;
    }

    /** ADMIN / MEMBER. 옛 토큰이면 null */
    public String getPrincipalType() {
        return principalType;
    }

    /** app_user.id 또는 members.id. 옛 토큰이면 null */
    public Long getPrincipalId() {
        return principalId;
    }

    public boolean isAdminAccount() {
        return TYPE_ADMIN.equals(principalType);
    }

    public boolean hasIdentity() {
        return principalType != null && principalId != null;
    }
}
