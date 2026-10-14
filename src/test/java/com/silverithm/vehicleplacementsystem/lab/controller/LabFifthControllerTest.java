package com.silverithm.vehicleplacementsystem.lab.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBudgetDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareEventDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabRehabSessionDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabBudgetService;
import com.silverithm.vehicleplacementsystem.lab.service.LabCareEventService;
import com.silverithm.vehicleplacementsystem.lab.service.LabGuardianNoticeService;
import com.silverithm.vehicleplacementsystem.lab.service.LabRehabSessionService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 5차 컨트롤러 — 경로·JSON 필드 이름·오류 형식(400/409/204)이 계약대로 나간다. */
class LabFifthControllerTest {

    private final LabCareEventService careEvents = mock(LabCareEventService.class);
    private final LabRehabSessionService rehab = mock(LabRehabSessionService.class);
    private final LabGuardianNoticeService notices = mock(LabGuardianNoticeService.class);
    private final LabBudgetService budgets = mock(LabBudgetService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabCareEventController(careEvents),
                        new LabRehabSessionController(rehab), new LabGuardianNoticeController(notices),
                        new LabBudgetController(budgets))
                .setControllerAdvice(new LabExceptionHandler())
                .build();
    }

    // 사고 응답의 필드 이름과 시각 모양('YYYY-MM-DDTHH:MM')
    @Test
    void careEventJsonShape() throws Exception {
        when(careEvents.list("2026-10", null, null)).thenReturn(List.of(new LabCareEventDto.View(1L, 2L, "김순자",
                "2026-10-14T09:05", "FALL", "HIGH", "복도", "넘어짐", null, "2026-10-14T09:30", "PHONE", null, null,
                "관찰", null, "OPEN", null, "박간호사", false, null)));
        mvc.perform(get("/api/v1/lab/care-events").param("month", "2026-10")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occurredAt").value("2026-10-14T09:05"))
                .andExpect(jsonPath("$[0].guardianNotifiedAt").value("2026-10-14T09:30"))
                .andExpect(jsonPath("$[0].elderlyName").value("김순자"))
                .andExpect(jsonPath("$[0].aiAssisted").value(false))
                .andExpect(jsonPath("$[0].reporterName").value("박간호사"));
        mvc.perform(delete("/api/v1/lab/care-events/1")).andExpect(status().isNoContent());
        verify(careEvents).delete(1L);
    }

    // 닫기 규칙 400, 형식이 틀린 본문 400
    @Test
    void careEventErrors() throws Exception {
        when(careEvents.create(any())).thenThrow(
                new CustomException("후속 조치를 적어야 닫을 수 있습니다", HttpStatus.BAD_REQUEST));
        mvc.perform(post("/api/v1/lab/care-events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderlyId\":1,\"occurredAt\":\"2026-10-14T09:00\",\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("후속 조치를 적어야 닫을 수 있습니다"));
        mvc.perform(post("/api/v1/lab/care-events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderlyId\":\"abc\"}")).andExpect(status().isBadRequest());
    }

    // batch 본문이 읽히고 결과 배열이 나간다
    @Test
    void rehabBatchRoute() throws Exception {
        when(rehab.createBatch(any())).thenReturn(List.of(new LabRehabSessionDto.View(1L, 2L, "김순자",
                LocalDate.of(2026, 10, 14), "ADL", 30, List.of("걷기"), null, null, "최치료사", false, null)));
        mvc.perform(post("/api/v1/lab/rehab-sessions/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderlyIds\":[2,3],\"sessionDate\":\"2026-10-14\",\"kind\":\"ADL\","
                                + "\"minutes\":30,\"items\":[\"걷기\"],\"therapistName\":\"최치료사\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].items[0]").value("걷기"))
                .andExpect(jsonPath("$[0].minutes").value(30));
        mvc.perform(get("/api/v1/lab/rehab-sessions").param("date", "2026-10-14")).andExpect(status().isOk());
        verify(rehab).list("2026-10-14", null, null);
    }

    // 보낸 안내 잠금과 예산 잠금은 409 + message
    @Test
    void lockedConflicts() throws Exception {
        when(notices.upsert(any())).thenThrow(new CustomException("보낸 안내입니다. 보냄 표시를 풀고 고쳐 주세요",
                HttpStatus.CONFLICT));
        mvc.perform(put("/api/v1/lab/guardian-notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderlyId\":1,\"noticeMonth\":\"2026-10\",\"title\":\"t\",\"body\":\"b\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("보낸 안내입니다. 보냄 표시를 풀고 고쳐 주세요"));

        when(budgets.upsert(any())).thenThrow(new CustomException("확정된 예산입니다. 확정을 풀고 고쳐 주세요",
                HttpStatus.CONFLICT));
        mvc.perform(put("/api/v1/lab/budgets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fiscalYear\":2026,\"revision\":0,\"lines\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("확정된 예산입니다. 확정을 풀고 고쳐 주세요"));
    }

    // 예산 응답에 줄이 중첩되고 확정·풀기 경로가 있다
    @Test
    void budgetShapeAndRoutes() throws Exception {
        LabBudgetDto.View view = new LabBudgetDto.View(7L, 2026, 0, null, "CONFIRMED", null, null,
                List.of(new LabBudgetDto.LineView("INCOME", "401", 1000, null)), null);
        when(budgets.confirm(7L)).thenReturn(view);
        when(budgets.unconfirm(7L)).thenReturn(view);
        when(budgets.listByYear("2026")).thenReturn(List.of(view));
        mvc.perform(post("/api/v1/lab/budgets/7/confirm")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].accountCode").value("401"))
                .andExpect(jsonPath("$.fiscalYear").value(2026));
        mvc.perform(post("/api/v1/lab/budgets/7/unconfirm")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/lab/budgets").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revision").value(0));
        mvc.perform(post("/api/v1/lab/guardian-notices/3/unsent")).andExpect(status().isOk());
        verify(notices).markUnsent(3L);
    }
}
