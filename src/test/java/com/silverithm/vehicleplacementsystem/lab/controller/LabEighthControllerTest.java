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
import com.silverithm.vehicleplacementsystem.lab.dto.LabCommitteeDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicalVisitDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffingDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCommitteeService;
import com.silverithm.vehicleplacementsystem.lab.service.LabElderDocumentService;
import com.silverithm.vehicleplacementsystem.lab.service.LabMedicalVisitService;
import com.silverithm.vehicleplacementsystem.lab.service.LabStaffingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 8차 컨트롤러 — 경로·JSON 필드 이름·오류 형식(400/404/204)이 계약대로 나간다. */
class LabEighthControllerTest {

    private final LabStaffingService staffing = mock(LabStaffingService.class);
    private final LabElderDocumentService documents = mock(LabElderDocumentService.class);
    private final LabMedicalVisitService visits = mock(LabMedicalVisitService.class);
    private final LabCommitteeService committee = mock(LabCommitteeService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabStaffingController(staffing),
                        new LabElderDocumentController(documents), new LabMedicalVisitController(visits),
                        new LabCommitteeMemberController(committee), new LabCommitteeMeetingController(committee))
                .setControllerAdvice(new LabExceptionHandler())
                .build();
    }

    // StaffingStaff 필드 이름(memberId, name, jobType, hours)과 월 조회 404, month·year 동시/없음 400
    @Test
    void staffingJsonShapeAndErrors() throws Exception {
        when(staffing.getByMonth("2026-03")).thenReturn(new LabStaffingDto.View(1L, "2026-03", 22, 440, null,
                List.of("2026-03-02"), List.of(new LabStaffingDto.Staff(7L, "이요양", "CAREGIVER",
                        new BigDecimal("176.5"))), null, "김센터", null));
        mvc.perform(get("/api/v1/lab/staffing").param("month", "2026-03")).andExpect(status().isOk())
                .andExpect(jsonPath("$.staffingMonth").value("2026-03"))
                .andExpect(jsonPath("$.staff[0].memberId").value(7))
                .andExpect(jsonPath("$.staff[0].name").value("이요양"))
                .andExpect(jsonPath("$.staff[0].jobType").value("CAREGIVER"))
                .andExpect(jsonPath("$.staff[0].hours").value(176.5))
                .andExpect(jsonPath("$.holidays[0]").value("2026-03-02"));
        when(staffing.getByMonth("2026-04")).thenThrow(new CustomException("해당 월 인력배치 기록이 없습니다",
                HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/v1/lab/staffing").param("month", "2026-04")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 월 인력배치 기록이 없습니다"));
        when(staffing.listByYear("2026")).thenReturn(List.of());
        mvc.perform(get("/api/v1/lab/staffing").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/v1/lab/staffing")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/lab/staffing").param("month", "2026-03").param("year", "2026"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/lab/staffing/4")).andExpect(status().isNoContent());
        verify(staffing).delete(4L);
    }

    // PUT 본문이 서비스 입력으로 묶인다(hours는 숫자)
    @Test
    void staffingBodyBinds() throws Exception {
        when(staffing.upsert(org.mockito.ArgumentMatchers.any())).thenReturn(new LabStaffingDto.View(1L, "2026-03",
                22, 440, null, List.of(), List.of(), null, null, null));
        mvc.perform(put("/api/v1/lab/staffing").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"staffingMonth\":\"2026-03\",\"serviceDays\":22,\"usersSum\":440,\"holidays\":[],"
                                + "\"staff\":[{\"memberId\":null,\"name\":\"김\",\"jobType\":\"COOK\",\"hours\":8.5}]}"))
                .andExpect(status().isOk());
        verify(staffing).upsert(new LabStaffingDto.Input("2026-03", 22L, 440L, null, List.of(),
                List.of(new LabStaffingDto.Staff(null, "김", "COOK", new BigDecimal("8.5"))), null, null));
    }

    // 진료 동행 필터 파라미터와 JSON 이름, 운영위원·회의 경로
    @Test
    void medicalAndCommitteeShapes() throws Exception {
        when(visits.list(null, null, true)).thenReturn(List.of(new LabMedicalVisitDto.View(3L, 5L, "김어르신",
                LocalDate.of(2026, 3, 10), "10:00", "11:00", "숲속내과", "내과", "사유", "결과", "처방", "이요양", true,
                "메모", LocalDate.of(2026, 3, 20), false, false, null)));
        mvc.perform(get("/api/v1/lab/medical-visits").param("followUpDue", "true")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].elderlyName").value("김어르신"))
                .andExpect(jsonPath("$[0].escortName").value("이요양"))
                .andExpect(jsonPath("$[0].followUpOn").exists())
                .andExpect(jsonPath("$[0].followUpDone").value(false))
                .andExpect(jsonPath("$[0].guardianNotified").value(true));
        when(visits.list(null, null, null)).thenThrow(new CustomException("조회 조건 필요", HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/v1/lab/medical-visits")).andExpect(status().isBadRequest());

        when(committee.listMembers()).thenReturn(List.of(new LabCommitteeDto.MemberView(1L, "홍길동", "CHAIR",
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31), true, null, null)));
        mvc.perform(get("/api/v1/lab/committee-members")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].termEnd").exists())
                .andExpect(jsonPath("$[0].active").value(true));
        when(committee.listMeetings("2026")).thenReturn(List.of(new LabCommitteeDto.MeetingView(2L,
                LocalDate.of(2026, 3, 10), "REGULAR", List.of(1L), List.of("홍길동"), "안건", "회의록",
                List.of("결정"), null, "김센터", false, null)));
        mvc.perform(get("/api/v1/lab/committee-meetings").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attendeeNames[0]").value("홍길동"))
                .andExpect(jsonPath("$[0].attendeeIds[0]").value(1))
                .andExpect(jsonPath("$[0].decisions[0]").value("결정"));
        mvc.perform(delete("/api/v1/lab/committee-meetings/9")).andExpect(status().isNoContent());
        verify(committee).deleteMeeting(9L);
        mvc.perform(delete("/api/v1/lab/committee-members/8")).andExpect(status().isNoContent());
        verify(committee).deleteMember(8L);
        mvc.perform(get("/api/v1/lab/elder-documents").param("elderlyId", "5")).andExpect(status().isOk());
        verify(documents).list(5L);
    }
}
