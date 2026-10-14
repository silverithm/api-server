package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.silverithm.vehicleplacementsystem.lab.LabGuardInterceptor;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 실험실 가드 — 열리지 않은 기관에는 항상 같은 문구의 403 JSON. */
class LabGuardInterceptorTest {

    private final LabAccessService access = mock(LabAccessService.class);
    private final LabGuardInterceptor interceptor = new LabGuardInterceptor(access);

    // 열려 있지 않으면 403과 message JSON으로 막는다
    @Test
    void blocksWhenNotEnabled() throws Exception {
        when(access.isEnabled()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lab/care-records");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"message\":\"실험실은 아직 이 기관에 열려 있지 않습니다\"}");
    }

    // 열려 있으면 그대로 통과시킨다
    @Test
    void passesWhenEnabled() throws Exception {
        when(access.isEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lab/care-records");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // CORS preflight는 인증 없이 지나간다
    @Test
    void optionsPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/lab/care-records");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
