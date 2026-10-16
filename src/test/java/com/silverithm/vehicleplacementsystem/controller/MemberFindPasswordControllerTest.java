package com.silverithm.vehicleplacementsystem.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.FindPasswordResponse;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 직원 비밀번호 찾기의 응답은 message·error 모두 문자열이다.
 * 앱은 message를 그대로 띄우고, 웹은 error를 그대로 보여 준다 — 객체로 감싸면 둘 다 깨진다.
 */
class MemberFindPasswordControllerTest {

    private final MemberService memberService = mock(MemberService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MemberController(memberService, mock(RedisUtils.class))).build();
    }

    // 보냈으면 200과 문자열 message
    @Test
    void sentReturnsPlainMessage() throws Exception {
        when(memberService.findPassword("staff@example.com"))
                .thenReturn(new FindPasswordResponse("임시 비밀번호가 이메일로 전송되었습니다."));

        mvc.perform(post("/api/v1/members/find/password").param("email", "staff@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("임시 비밀번호가 이메일로 전송되었습니다."));
    }

    // 가입된 이메일이 없으면 500이 아니라 404와 그 사유
    @Test
    void unknownEmailKeepsStatusAndReason() throws Exception {
        when(memberService.findPassword("nobody@example.com"))
                .thenThrow(new CustomException("해당 이메일로 가입된 사용자가 없습니다.", HttpStatus.NOT_FOUND));

        mvc.perform(post("/api/v1/members/find/password").param("email", "nobody@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("해당 이메일로 가입된 사용자가 없습니다."));
    }

    // 예상 못 한 오류는 500과 문자열 error
    @Test
    void unexpectedFailureIsPlainServerError() throws Exception {
        when(memberService.findPassword("staff@example.com")).thenThrow(new IllegalStateException("메일 서버 없음"));

        mvc.perform(post("/api/v1/members/find/password").param("email", "staff@example.com"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("비밀번호 찾기 중 오류가 발생했습니다"));
    }
}
