package com.silverithm.vehicleplacementsystem.lab.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.service.LabMealPlanService;
import com.silverithm.vehicleplacementsystem.lab.service.LabSafetyCheckService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 3차 컨트롤러 — 조회 조건 조합 오류, 404, 형식이 틀린 본문, 삭제 204가 계약대로 나간다. */
class LabOperationsControllerTest {

    private final LabMealPlanService meals = mock(LabMealPlanService.class);
    private final LabSafetyCheckService safety = mock(LabSafetyCheckService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabMealPlanController(meals), new LabSafetyCheckController(safety))
                .setControllerAdvice(new LabExceptionHandler())
                .build();
    }

    // 식단: 조건이 없거나 둘 다 있으면 400, 없는 주는 서비스의 404가 message로 나간다
    @Test
    void mealPlanQueryRules() throws Exception {
        mvc.perform(get("/api/v1/lab/meal-plans")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/api/v1/lab/meal-plans").param("weekStart", "2026-10-12").param("month", "2026-10"))
                .andExpect(status().isBadRequest());

        when(meals.getByWeek("2026-10-19")).thenThrow(new CustomException("해당 주 식단이 없습니다", HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/v1/lab/meal-plans").param("weekStart", "2026-10-19")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 주 식단이 없습니다"));

        when(meals.listByMonth("2026-10")).thenReturn(List.of());
        mvc.perform(get("/api/v1/lab/meal-plans").param("month", "2026-10")).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // 점검: month와 year를 함께 주면 400, 둘 다 없어도 400
    @Test
    void safetyQueryRules() throws Exception {
        mvc.perform(get("/api/v1/lab/safety-checks").param("month", "2026-10").param("year", "2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/api/v1/lab/safety-checks")).andExpect(status().isBadRequest());

        when(safety.listByYear("2026", "DRILL")).thenReturn(List.of());
        mvc.perform(get("/api/v1/lab/safety-checks").param("year", "2026").param("kind", "DRILL"))
                .andExpect(status().isOk());
        verify(safety).listByYear("2026", "DRILL");
    }

    // 형식이 틀린 본문(날짜가 아닌 값, 배열 자리에 객체)은 500이 아니라 400
    @Test
    void malformedBodiesAreBadRequests() throws Exception {
        mvc.perform(put("/api/v1/lab/meal-plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStart\":\"월요일\",\"days\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(put("/api/v1/lab/meal-plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStart\":\"2026-10-12\",\"days\":{\"a\":1}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/lab/safety-checks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"FIRE\",\"checkDate\":\"2026-10-14\",\"items\":[{\"code\":\"AB\",\"ok\":\"아니오\"}]}"))
                .andExpect(status().isBadRequest());
    }

    // 삭제는 204
    @Test
    void deleteReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/v1/lab/meal-plans/5")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/lab/safety-checks/7")).andExpect(status().isNoContent());
        verify(meals).delete(5L);
        verify(safety).delete(7L);
    }
}
