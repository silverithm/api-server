package com.silverithm.vehicleplacementsystem.lab.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.service.LabCareRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 실험실 컨트롤러의 오류 응답은 전역 핸들러의 {"error"}가 아니라 계약의 {"message"}로 나간다. */
class LabControllerErrorTest {

    private final LabCareRecordService service = mock(LabCareRecordService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabCareRecordController(service))
                .setControllerAdvice(new LabExceptionHandler())
                .build();
    }

    // 서비스 오류는 상태 코드와 message JSON으로 나간다
    @Test
    void customExceptionBecomesMessageJson() throws Exception {
        when(service.upsert(any())).thenThrow(new CustomException("어르신을 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        mvc.perform(put("/api/v1/lab/care-records").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderlyId\":999,\"recordDate\":\"2026-10-14\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("어르신을 찾을 수 없습니다"));
    }

    // 날짜 형식이 틀린 본문과 조회 조건이 빠진 요청은 400
    @Test
    void malformedRequestsAreBadRequests() throws Exception {
        mvc.perform(put("/api/v1/lab/care-records").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderlyId\":1,\"recordDate\":\"내일\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        mvc.perform(get("/api/v1/lab/care-records"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("message")));
    }
}
