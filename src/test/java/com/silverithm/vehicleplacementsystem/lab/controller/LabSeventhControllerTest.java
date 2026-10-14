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
import com.silverithm.vehicleplacementsystem.lab.dto.LabBusinessPlanDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabIntakeDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramPlanDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabSatisfactionDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabBusinessPlanService;
import com.silverithm.vehicleplacementsystem.lab.service.LabIntakeService;
import com.silverithm.vehicleplacementsystem.lab.service.LabProgramPlanService;
import com.silverithm.vehicleplacementsystem.lab.service.LabSatisfactionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 7차 컨트롤러 — 경로·JSON 필드 이름·오류 형식(400/404/204)이 계약대로 나간다. */
class LabSeventhControllerTest {

    private final LabProgramPlanService programs = mock(LabProgramPlanService.class);
    private final LabBusinessPlanService business = mock(LabBusinessPlanService.class);
    private final LabSatisfactionService satisfaction = mock(LabSatisfactionService.class);
    private final LabIntakeService intake = mock(LabIntakeService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabProgramPlanController(programs),
                        new LabProgramScheduleController(programs), new LabProgramFeedbackController(programs),
                        new LabBusinessPlanController(business), new LabSatisfactionController(satisfaction),
                        new LabIntakeController(intake))
                .setControllerAdvice(new LabExceptionHandler())
                .build();
    }

    // 계획표 항목의 필드 이름(date, startTime, category, title, instructor)과 달 조회 404
    @Test
    void scheduleJsonShapeAndNotFound() throws Exception {
        when(programs.getSchedule("2026-03")).thenReturn(new LabProgramPlanDto.ScheduleView(1L, "2026-03",
                List.of(new LabProgramPlanDto.ScheduleItem("2026-03-10", "10:30", "COGNITIVE", "퍼즐", "김강사")), "메모",
                LocalDate.of(2026, 2, 27), "PRINT", false, null));
        mvc.perform(get("/api/v1/lab/program-schedules").param("month", "2026-03")).andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleMonth").value("2026-03"))
                .andExpect(jsonPath("$.items[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$.items[0].startTime").value("10:30"))
                .andExpect(jsonPath("$.items[0].category").value("COGNITIVE"))
                .andExpect(jsonPath("$.items[0].title").value("퍼즐"))
                .andExpect(jsonPath("$.items[0].instructor").value("김강사"))
                .andExpect(jsonPath("$.sharedMethod").value("PRINT"));
        when(programs.getSchedule("2026-04")).thenThrow(new CustomException("해당 월 계획표가 없습니다",
                HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/v1/lab/program-schedules").param("month", "2026-04")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 월 계획표가 없습니다"));
        when(programs.listSchedules("2026")).thenReturn(List.of());
        mvc.perform(get("/api/v1/lab/program-schedules").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/v1/lab/program-schedules")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/lab/program-schedules").param("month", "2026-03").param("year", "2026"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/lab/program-schedules/4")).andExpect(status().isNoContent());
        verify(programs).deleteSchedule(4L);
    }

    // PUT upsert와 POST·PUT·DELETE가 본문을 그대로 서비스 입력으로 넘긴다
    @Test
    void bodiesBindAndErrorsAreMessages() throws Exception {
        when(programs.upsertPlan(any())).thenReturn(new LabProgramPlanDto.PlanView(1L, 2026, "SOCIAL", "목표", "내용",
                "월 1회", null, 100L, LocalDate.of(2026, 1, 5), "박사회", false, null));
        mvc.perform(put("/api/v1/lab/program-plans").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"planYear\":2026,\"category\":\"SOCIAL\",\"goal\":\"목표\",\"content\":\"내용\","
                                + "\"frequency\":\"월 1회\",\"writtenOn\":\"2026-01-05\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planYear").value(2026))
                .andExpect(jsonPath("$.writerName").value("박사회"));
        mvc.perform(put("/api/v1/lab/program-plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planYear\":2026,\"writtenOn\":\"어제\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());

        when(programs.createFeedback(any())).thenReturn(new LabProgramPlanDto.FeedbackView(5L, "2026-H1",
                LocalDate.of(2026, 3, 1), "SURVEY", 10, "요약", null, null, null, false, null));
        mvc.perform(post("/api/v1/lab/program-feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"half\":\"2026-H1\",\"collectedOn\":\"2026-03-01\",\"method\":\"SURVEY\","
                                + "\"participants\":10,\"summary\":\"요약\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.half").value("2026-H1"))
                .andExpect(jsonPath("$.participants").value(10));
        mvc.perform(delete("/api/v1/lab/program-feedback/5")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/lab/program-plans/6")).andExpect(status().isNoContent());
    }

    // 사업계획 항목 필드 이름(name, goal, content, target, budget, schedule)과 없으면 404
    @Test
    void businessPlanJsonShape() throws Exception {
        LabBusinessPlanDto.Item item = new LabBusinessPlanDto.Item("프로그램 운영", "목표", "내용", "전체", 2_000_000L, "연중");
        when(business.getByYear("2026")).thenReturn(new LabBusinessPlanDto.View(1L, 2026,
                new LabBusinessPlanDto.Sections(List.of(), List.of(item), List.of()), 5L, 4L, null, null, false,
                null));
        mvc.perform(get("/api/v1/lab/business-plans").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.operation").isEmpty())
                .andExpect(jsonPath("$.sections.service[0].name").value("프로그램 운영"))
                .andExpect(jsonPath("$.sections.service[0].budget").value(2_000_000))
                .andExpect(jsonPath("$.sections.service[0].schedule").value("연중"))
                .andExpect(jsonPath("$.totalExpense").value(4));
        when(business.getByYear("2027")).thenThrow(new CustomException("해당 연도 사업계획서가 없습니다",
                HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/v1/lab/business-plans").param("year", "2027")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 연도 사업계획서가 없습니다"));
        when(business.upsert(any())).thenReturn(new LabBusinessPlanDto.View(1L, 2026,
                new LabBusinessPlanDto.Sections(List.of(), List.of(), List.of()), null, null, null, null, false, null));
        mvc.perform(put("/api/v1/lab/business-plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planYear\":2026,\"sections\":{\"operation\":[],\"service\":[{\"name\":\"a\","
                                + "\"budget\":10}],\"staff\":[]}}"))
                .andExpect(status().isOk());
    }

    // 만족도 점수 맵·입소 상담 응답, 기간 없는 조회는 서비스의 400 메시지가 그대로 나간다
    @Test
    void satisfactionAndIntakeShapes() throws Exception {
        when(satisfaction.list("MEAL", "2026-01-01", "2026-12-31")).thenReturn(List.of(new LabSatisfactionDto.View(1L,
                LocalDate.of(2026, 3, 10), "MEAL", null, null, "SELF", Map.of("MEAL_TASTE", 4), "의견", "박사회",
                null)));
        mvc.perform(get("/api/v1/lab/satisfaction").param("kind", "MEAL").param("from", "2026-01-01")
                        .param("to", "2026-12-31")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scores.MEAL_TASTE").value(4))
                .andExpect(jsonPath("$[0].elderlyId").isEmpty())
                .andExpect(jsonPath("$[0].interviewerName").value("박사회"));
        when(satisfaction.list(null, null, null)).thenThrow(new CustomException("조회 기간(from, to)을 모두 보내 주세요",
                HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/v1/lab/satisfaction")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("조회 기간(from, to)을 모두 보내 주세요"));

        when(intake.create(any())).thenThrow(new CustomException("개인정보 수집·이용 동의일을 적어야 연락처를 저장할 수 있습니다",
                HttpStatus.BAD_REQUEST));
        mvc.perform(post("/api/v1/lab/intake").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inquiryDate\":\"2026-03-10\",\"applicantName\":\"홍길동\","
                                + "\"contactPhone\":\"010-1234-5678\",\"source\":\"PHONE\",\"status\":\"NEW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("개인정보 수집·이용 동의일을 적어야 연락처를 저장할 수 있습니다"));
        when(intake.list("WAITING")).thenReturn(List.of(new LabIntakeDto.View(2L, LocalDate.of(2026, 3, 10), "홍길동",
                null, null, null, "3", null, null, "PHONE", "WAITING", null, null, null, null, null, null, null, false,
                null)));
        mvc.perform(get("/api/v1/lab/intake").param("status", "WAITING")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicantName").value("홍길동"))
                .andExpect(jsonPath("$[0].careGrade").value("3"))
                .andExpect(jsonPath("$[0].status").value("WAITING"));
        mvc.perform(put("/api/v1/lab/intake/2").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/lab/intake/2")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/lab/satisfaction/1")).andExpect(status().isNoContent());
        verify(intake).delete(2L);
    }
}
