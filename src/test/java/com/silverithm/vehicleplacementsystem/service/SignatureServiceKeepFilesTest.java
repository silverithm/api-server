package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 내 서명을 바꾸거나 지워도 예전 서명 파일은 남는다(2026-11-16).
 *
 * <p>결재 단계·회의록 서명은 서명할 때의 파일 경로를 그대로 가리킨다. 서명을 바꿀 때 옛 파일을 지우던 탓에
 * 숲속 원장(김도형)이 서명을 두 번 바꾸자 지난 결재 372건·회의록 서명의 그림이 깨졌다.
 */
class SignatureServiceKeepFilesTest {

    private static final String 옛서명 = "signatures/edbb9f71-0dd8-47be-bcd4-7de1a168fc51.png";
    private static final String 새서명 = "signatures/bfee5430-c296-4e79-9749-3134924b6cee.png";
    /** 가장 작은 PNG 머리(8바이트 서명 + 여유) — 서비스는 머리만 본다 */
    private static final String PNG = "data:image/png;base64,"
            + Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0});

    private FileStorageService storage;
    private AppUser 원장;
    private SignatureService service;
    private final UserDetails 로그인 = User.withUsername("director@forest.kr").password("x").roles("ADMIN").build();

    @BeforeEach
    void setUp() throws Exception {
        storage = mock(FileStorageService.class);
        when(storage.storeBytes(any(), eq(".png"), eq("signatures"))).thenReturn(새서명);
        when(storage.getFileUrl(anyString())).thenAnswer(inv -> "https://bucket/carev/" + inv.getArgument(0));

        MemberRepository members = mock(MemberRepository.class);
        when(members.findByUsername(anyString())).thenReturn(Optional.empty());
        원장 = mock(AppUser.class);
        when(원장.getSignatureUrl()).thenReturn(옛서명);
        when(원장.getId()).thenReturn(3L);
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("director@forest.kr")).thenReturn(Optional.of(원장));

        service = new SignatureService(members, users, storage);
    }

    @Test
    @DisplayName("서명을 바꿔도 옛 서명 파일은 지우지 않는다 — 지난 결재가 그 파일을 가리킨다")
    void 바꿔도옛파일은남는다() throws Exception {
        String url = service.registerMySignature(로그인, PNG);

        verify(원장).updateSignature(새서명);
        verify(storage, never()).deleteFile(anyString());
        assertThat(url).endsWith(새서명);
    }

    @Test
    @DisplayName("서명을 지워도 등록만 지우고 파일은 남긴다")
    void 지워도파일은남는다() throws Exception {
        service.deleteMySignature(로그인);

        verify(원장).updateSignature(null);
        verify(storage, never()).deleteFile(anyString());
    }
}
