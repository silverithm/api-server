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
import com.silverithm.vehicleplacementsystem.lab.dto.LabCarePlanChangeDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabGrievanceDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabOutbreakDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffWelfareDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCarePlanChangeService;
import com.silverithm.vehicleplacementsystem.lab.service.LabGrievanceSummaryService;
import com.silverithm.vehicleplacementsystem.lab.service.LabOutbreakService;
import com.silverithm.vehicleplacementsystem.lab.service.LabStaffWelfareService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 11차 컨트롤러 — 경로·JSON 필드 이름·오류 형식(400/404/204)이 계약대로 나간다. */
class LabEleventhControllerTest {

    private final LabOutbreakService outbreak = mock(LabOutbreakService.class);
    private final LabStaffWelfareService welfare = mock(LabStaffWelfareService.class);
    private final LabCarePlanChangeService change = mock(LabCarePlanChangeService.class);
    private final LabGrievanceSummaryService grievance = mock(LabGrievanceSummaryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabOutbreakController(outbreak),
                        new LabStaffWelfareController(welfare), new LabBonusFundController(welfare),
                        new LabCarePlanChangeController(change), new LabGrievanceSummaryController(grievance))
                .setControllerAdvice(new LabExceptionHandler()).build();
    }

    @Test
    void outbreakShapes() throws Exception {
        when(outbreak.list("2026")).thenReturn(List.of(new LabOutbreakDto.View(1L, "NATIONAL", "코로나19",
                LocalDate.of(2026, 2, 1), 0, 0, null, null, null, "ORANGE", List.of("MASK_HAND"), null, null,
                "원장", null)));
        mvc.perform(get("/api/v1/lab/outbreaks").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("NATIONAL"))
                .andExpect(jsonPath("$[0].startedOn").exists())
                .andExpect(jsonPath("$[0].alertLevel").value("ORANGE"))
                .andExpect(jsonPath("$[0].actions[0]").value("MASK_HAND"))
                .andExpect(jsonPath("$[0].healthCenterReportedOn").isEmpty())
                .andExpect(jsonPath("$[0].writerName").value("원장"));
        when(outbreak.create(any())).thenThrow(new CustomException("알 수 없는 조치가 들어 있습니다", HttpStatus.BAD_REQUEST));
        mvc.perform(post("/api/v1/lab/outbreaks").contentType(MediaType.APPLICATION_JSON).content(
                "{\"kind\":\"CASE\",\"disease\":\"독감\",\"startedOn\":\"2026-02-01\",\"elderCases\":1,"
                        + "\"staffCases\":0,\"actions\":[\"MAGIC\"]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("알 수 없는 조치가 들어 있습니다"));
        verify(outbreak).create(new LabOutbreakDto.Input("CASE", "독감", LocalDate.of(2026, 2, 1), 1, 0, null, null,
                null, null, List.of("MAGIC"), null, null));
        mvc.perform(put("/api/v1/lab/outbreaks/4").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(outbreak).update(org.mockito.ArgumentMatchers.eq(4L), any());
        mvc.perform(delete("/api/v1/lab/outbreaks/3")).andExpect(status().isNoContent());
        verify(outbreak).delete(3L);
    }

    @Test
    void welfareAndFundShapes() throws Exception {
        when(welfare.listWelfare("2026")).thenReturn(List.of(new LabStaffWelfareDto.WelfareView(1L,
                LocalDate.of(2026, 3, 5), 7L, "요양보호사", "GIFT_CARD", "상품권", 50_000L, true, false, 9L, null, null,
                null)));
        mvc.perform(get("/api/v1/lab/staff-welfare").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberName").value("요양보호사"))
                .andExpect(jsonPath("$[0].recipientIsOwner").value(false))
                .andExpect(jsonPath("$[0].evidenceLedgerId").value(9))
                .andExpect(jsonPath("$[0].amount").value(50000));
        when(welfare.createWelfare(any())).thenThrow(new CustomException("장부 기록을 찾을 수 없습니다",
                HttpStatus.NOT_FOUND));
        mvc.perform(post("/api/v1/lab/staff-welfare").contentType(MediaType.APPLICATION_JSON).content(
                "{\"givenOn\":\"2026-03-05\",\"memberId\":7,\"kind\":\"BONUS\",\"item\":\"포상금\",\"amount\":1,"
                        + "\"signed\":true,\"evidenceLedgerId\":99}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("장부 기록을 찾을 수 없습니다"));
        mvc.perform(delete("/api/v1/lab/staff-welfare/2")).andExpect(status().isNoContent());
        verify(welfare).deleteWelfare(2L);

        when(welfare.listFunds("2026")).thenReturn(List.of(new LabStaffWelfareDto.BonusView(1L, "SPENT",
                LocalDate.of(2026, 3, 5), 1_000_000L, null, null, "포상", true, null, null, null)));
        mvc.perform(get("/api/v1/lab/bonus-funds").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("SPENT"))
                .andExpect(jsonPath("$[0].ownerUse").value(true))
                .andExpect(jsonPath("$[0].memberId").isEmpty())
                .andExpect(jsonPath("$[0].occurredOn").exists());
        mvc.perform(delete("/api/v1/lab/bonus-funds/5")).andExpect(status().isNoContent());
        verify(welfare).deleteFund(5L);
    }

    @Test
    void carePlanChangeShapes() throws Exception {
        when(change.list(7L, null)).thenReturn(List.of(new LabCarePlanChangeDto.View(1L, 7L, "김어르신",
                LocalDate.of(2026, 4, 10), "ELDER_WISH", "사유", List.of("CONTENT", "TIME"), "내용", 3L, null, null,
                null, "원장", null)));
        mvc.perform(get("/api/v1/lab/care-plan-changes").param("elderlyId", "7")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].elderlyName").value("김어르신"))
                .andExpect(jsonPath("$[0].changeItems[1]").value("TIME"))
                .andExpect(jsonPath("$[0].eventId").value(3))
                .andExpect(jsonPath("$[0].notifiedOn").isEmpty());
        when(change.list(null, null)).thenThrow(new CustomException("elderlyId 또는 year 중 하나를 보내 주세요",
                HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/v1/lab/care-plan-changes")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("elderlyId 또는 year 중 하나를 보내 주세요"));
        mvc.perform(post("/api/v1/lab/care-plan-changes").contentType(MediaType.APPLICATION_JSON).content(
                "{\"elderlyId\":7,\"changedOn\":\"2026-04-10\",\"reason\":\"OTHER\",\"reasonNote\":\"사유\","
                        + "\"changeItems\":[\"TIME\"],\"changeDetail\":\"내용\",\"eventId\":3}"))
                .andExpect(status().isOk());
        verify(change).create(new LabCarePlanChangeDto.Input(7L, LocalDate.of(2026, 4, 10), "OTHER", "사유",
                List.of("TIME"), "내용", 3L, null, null, null));
        mvc.perform(delete("/api/v1/lab/care-plan-changes/8")).andExpect(status().isNoContent());
        verify(change).delete(8L);
    }

    @Test
    void grievanceSummaryShape() throws Exception {
        when(grievance.summary("2026")).thenReturn(new LabGrievanceDto.Summary(2026, 4, 2, 1, 1, 1, 1, 2, 2.5, 30));
        mvc.perform(get("/api/v1/lab/grievance-summary").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.inReview").value(1))
                .andExpect(jsonPath("$.onHold").value(1))
                .andExpect(jsonPath("$.replied").value(2))
                .andExpect(jsonPath("$.medianReplyDays").value(2.5))
                .andExpect(jsonPath("$.oldestOpenDays").value(30))
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.authorName").doesNotExist());
        when(grievance.summary(null)).thenThrow(new CustomException("연도을(를) 입력해 주세요", HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/v1/lab/grievance-summary")).andExpect(status().isBadRequest());
    }
}
