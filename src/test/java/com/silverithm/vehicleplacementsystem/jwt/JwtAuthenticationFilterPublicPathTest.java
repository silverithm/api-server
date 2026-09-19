package com.silverithm.vehicleplacementsystem.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtAuthenticationFilterPublicPathTest {

    @Test
    void 소켓_핸드셰이크와_공개_경로는_필터를_건너뛴다() {
        assertThat(JwtAuthenticationFilter.isPublicPath("/ws/chat")).isTrue();
        assertThat(JwtAuthenticationFilter.isPublicPath("/ws/chat/info")).isTrue();
        assertThat(JwtAuthenticationFilter.isPublicPath("/ws/chat/123/abcd/websocket")).isTrue();
        assertThat(JwtAuthenticationFilter.isPublicPath("/api/v1/app-version")).isTrue();
        assertThat(JwtAuthenticationFilter.isPublicPath("/api/v1/refresh-token")).isTrue();
    }

    @Test
    void 보호된_경로는_계속_필터를_거친다() {
        assertThat(JwtAuthenticationFilter.isPublicPath("/api/v1/chat/rooms")).isFalse();
        assertThat(JwtAuthenticationFilter.isPublicPath("/api/v1/schedules")).isFalse();
        assertThat(JwtAuthenticationFilter.isPublicPath("/wsx")).isFalse();
        assertThat(JwtAuthenticationFilter.isPublicPath(null)).isFalse();
    }
}
