package com.silverithm.vehicleplacementsystem.lab.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import com.silverithm.vehicleplacementsystem.lab.dto.LabCaseMeetingDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabPolicyDocDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabTransportDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabVaccinationDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCaseMeetingService;
import com.silverithm.vehicleplacementsystem.lab.service.LabPolicyDocService;
import com.silverithm.vehicleplacementsystem.lab.service.LabTransportService;
import com.silverithm.vehicleplacementsystem.lab.service.LabVaccinationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 10차 컨트롤러 — 경로·JSON 필드 이름·오류 형식(400/404/409/204)이 계약대로 나간다. */
class LabTenthControllerTest {

    private final LabVaccinationService vaccination = mock(LabVaccinationService.class);
    private final LabCaseMeetingService caseMeeting = mock(LabCaseMeetingService.class);
    private final LabTransportService transport = mock(LabTransportService.class);
    private final LabPolicyDocService policy = mock(LabPolicyDocService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabVaccinationController(vaccination),
                        new LabCaseMeetingController(caseMeeting), new LabTransportLogController(transport),
                        new LabTransportVehicleController(transport), new LabGuideDeliveryController(transport),
                        new LabPolicyDocController(policy))
                .setControllerAdvice(new LabExceptionHandler()).build();
    }

    @Test
    void vaccinationShapes() throws Exception {
        when(vaccination.list("2025", null)).thenReturn(List.of(new LabVaccinationDto.View(1L, 7L, "김어르신", 2025,
                "INFLUENZA", "DONE", LocalDate.of(2025, 10, 20), "행복의원", "CERT", null, null, "admin", null)));
        mvc.perform(get("/api/v1/lab/vaccinations").param("year", "2025")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].elderlyId").value(7))
                .andExpect(jsonPath("$[0].elderlyName").value("김어르신"))
                .andExpect(jsonPath("$[0].seasonYear").value(2025))
                .andExpect(jsonPath("$[0].vaccine").value("INFLUENZA"))
                .andExpect(jsonPath("$[0].givenOn").exists())
                .andExpect(jsonPath("$[0].verifiedBy").value("CERT"))
                .andExpect(jsonPath("$[0].writerName").value("admin"));
        when(vaccination.list(null, null)).thenThrow(new CustomException("year 또는 elderlyId 중 하나를 보내 주세요",
                HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/v1/lab/vaccinations")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("year 또는 elderlyId 중 하나를 보내 주세요"));
        when(vaccination.upsert(any())).thenReturn(new LabVaccinationDto.View(1L, 7L, "김어르신", 2025, "INFLUENZA",
                "REFUSED", null, null, null, "거부", null, "admin", null));
        mvc.perform(put("/api/v1/lab/vaccinations").contentType(MediaType.APPLICATION_JSON).content(
                "{\"elderlyId\":7,\"seasonYear\":2025,\"vaccine\":\"INFLUENZA\",\"status\":\"REFUSED\","
                        + "\"reason\":\"거부\"}")).andExpect(status().isOk());
        verify(vaccination).upsert(new LabVaccinationDto.Input(7L, 2025, "INFLUENZA", "REFUSED", null, null, null,
                "거부", null));
        when(vaccination.upsert(any())).thenThrow(new CustomException("어르신을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        mvc.perform(put("/api/v1/lab/vaccinations").contentType(MediaType.APPLICATION_JSON).content(
                "{\"elderlyId\":9,\"seasonYear\":2025,\"vaccine\":\"INFLUENZA\",\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/lab/vaccinations/3")).andExpect(status().isNoContent());
        verify(vaccination).delete(3L);
    }

    @Test
    void caseMeetingShapes() throws Exception {
        when(caseMeeting.list("2026")).thenReturn(List.of(new LabCaseMeetingDto.View(1L, LocalDate.of(2026, 3, 10),
                List.of(new LabCaseMeetingDto.Attendee(5L, "원장")),
                List.of(new LabCaseMeetingDto.Opinion(5L, "원장", null)),
                List.of(new LabCaseMeetingDto.Target(7L, "김어르신", "사유", "계획", 5L, "원장",
                        LocalDate.of(2026, 3, 12), "CARE_PLAN", null, null, null, null, null)),
                "회의록", "admin", true, null)));
        mvc.perform(get("/api/v1/lab/case-meetings").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].heldOn").exists())
                .andExpect(jsonPath("$[0].attendees[0].memberName").value("원장"))
                .andExpect(jsonPath("$[0].opinions[0].memberId").value(5))
                .andExpect(jsonPath("$[0].targets[0].elderlyName").value("김어르신"))
                .andExpect(jsonPath("$[0].targets[0].ownerName").value("원장"))
                .andExpect(jsonPath("$[0].targets[0].reflectMethod").value("CARE_PLAN"))
                .andExpect(jsonPath("$[0].targets[0].reflectedOn").exists())
                .andExpect(jsonPath("$[0].aiAssisted").value(true));
        when(caseMeeting.create(any())).thenThrow(new CustomException("직원을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        String body = "{\"heldOn\":\"2026-03-10\",\"attendeeIds\":[5,6],\"opinions\":[{\"memberId\":5,\"opinion\":null}],"
                + "\"targets\":[{\"elderlyId\":7,\"reason\":\"사유\",\"plan\":\"계획\",\"ownerMemberId\":null,"
                + "\"reflectedOn\":\"2026-03-12\",\"reflectMethod\":\"CARE_PLAN\",\"resolved\":null}],"
                + "\"minutes\":null,\"aiAssisted\":false}";
        mvc.perform(post("/api/v1/lab/case-meetings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("직원을 찾을 수 없습니다"));
        verify(caseMeeting).create(new LabCaseMeetingDto.Input(LocalDate.of(2026, 3, 10), List.of(5L, 6L),
                List.of(new LabCaseMeetingDto.OpinionInput(5L, null)),
                List.of(new LabCaseMeetingDto.TargetInput(7L, "사유", "계획", null, LocalDate.of(2026, 3, 12),
                        "CARE_PLAN", null, null, null, null, null)), null, false));
        when(caseMeeting.update(any(), any())).thenThrow(new CustomException("반영일은 회의일보다 앞설 수 없습니다",
                HttpStatus.BAD_REQUEST));
        mvc.perform(put("/api/v1/lab/case-meetings/4").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/lab/case-meetings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"heldOn\":\"내일\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/lab/case-meetings/4")).andExpect(status().isNoContent());
        verify(caseMeeting).delete(4L);
    }

    @Test
    void transportShapes() throws Exception {
        when(transport.listLogs("2026-03-10", null)).thenReturn(List.of(new LabTransportDto.LogView(1L,
                LocalDate.of(2026, 3, 10), "PICKUP", "12가3456", 5L, "운전원", 6L, "동승자", "08:00", "09:10",
                List.of(new LabTransportDto.Rider(7L, "김어르신", true, "08:20", null, null)), null, "admin", null)));
        mvc.perform(get("/api/v1/lab/transport-logs").param("date", "2026-03-10")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runDate").exists())
                .andExpect(jsonPath("$[0].vehicleNo").value("12가3456"))
                .andExpect(jsonPath("$[0].driverName").value("운전원"))
                .andExpect(jsonPath("$[0].escortName").value("동승자"))
                .andExpect(jsonPath("$[0].departTime").value("08:00"))
                .andExpect(jsonPath("$[0].arriveTime").value("09:10"))
                .andExpect(jsonPath("$[0].riders[0].boardTime").value("08:20"))
                .andExpect(jsonPath("$[0].riders[0].boarded").value(true))
                .andExpect(jsonPath("$[0].riders[0].elderlyName").value("김어르신"));
        when(transport.listLogs(null, null)).thenThrow(new CustomException("date 또는 month 중 하나를 보내 주세요",
                HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/v1/lab/transport-logs")).andExpect(status().isBadRequest());
        when(transport.upsertLog(any())).thenThrow(new CustomException("운전원과 동승자는 다른 사람이어야 합니다",
                HttpStatus.BAD_REQUEST));
        mvc.perform(put("/api/v1/lab/transport-logs").contentType(MediaType.APPLICATION_JSON).content(
                "{\"runDate\":\"2026-03-10\",\"direction\":\"PICKUP\",\"vehicleNo\":\"12가3456\",\"driverMemberId\":5,"
                        + "\"escortMemberId\":5,\"departTime\":\"08:00\",\"arriveTime\":null,"
                        + "\"riders\":[{\"elderlyId\":7,\"boarded\":true,\"boardTime\":\"08:20\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("운전원과 동승자는 다른 사람이어야 합니다"));
        verify(transport).upsertLog(new LabTransportDto.LogInput(LocalDate.of(2026, 3, 10), "PICKUP", "12가3456", 5L,
                5L, "08:00", null, List.of(new LabTransportDto.RiderInput(7L, true, "08:20", null, null)), null));
        mvc.perform(delete("/api/v1/lab/transport-logs/2")).andExpect(status().isNoContent());
        verify(transport).deleteLog(2L);

        when(transport.listVehicles()).thenReturn(List.of(new LabTransportDto.VehicleView(1L, "12가3456", "카니발",
                "한화", "P-1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), true, true, null, null)));
        mvc.perform(get("/api/v1/lab/transport-vehicles")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleNo").value("12가3456"))
                .andExpect(jsonPath("$[0].insuranceTo").exists())
                .andExpect(jsonPath("$[0].comprehensive").value(true))
                .andExpect(jsonPath("$[0].active").value(true));
        when(transport.createVehicle(any())).thenThrow(new CustomException("이미 등록된 차량번호입니다",
                HttpStatus.CONFLICT));
        mvc.perform(post("/api/v1/lab/transport-vehicles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleNo\":\"12가3456\",\"comprehensive\":true}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 차량번호입니다"));
        mvc.perform(put("/api/v1/lab/transport-vehicles/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleNo\":\"12가3456\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/lab/transport-vehicles/1")).andExpect(status().isNoContent());
        verify(transport).deleteVehicle(1L);

        when(transport.listDeliveries("TRANSPORT_RULES", 7L)).thenReturn(List.of(new LabTransportDto.DeliveryView(1L,
                7L, "김어르신", "TRANSPORT_RULES", LocalDate.of(2026, 3, 10), "PAPER", null, null)));
        mvc.perform(get("/api/v1/lab/guide-deliveries").param("kind", "TRANSPORT_RULES").param("elderlyId", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].deliveredOn").exists())
                .andExpect(jsonPath("$[0].method").value("PAPER"))
                .andExpect(jsonPath("$[0].elderlyName").value("김어르신"));
        when(transport.createDeliveries(any())).thenReturn(List.of(new LabTransportDto.DeliveryView(2L, 7L, "김어르신",
                "TIMETABLE", LocalDate.of(2026, 3, 10), "SMS", null, null)));
        mvc.perform(post("/api/v1/lab/guide-deliveries/batch").contentType(MediaType.APPLICATION_JSON).content(
                "{\"kind\":\"TIMETABLE\",\"deliveredOn\":\"2026-03-10\",\"method\":\"SMS\",\"elderlyIds\":[7,8]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].kind").value("TIMETABLE"));
        verify(transport).createDeliveries(new LabTransportDto.DeliveryBatchInput("TIMETABLE",
                LocalDate.of(2026, 3, 10), "SMS", null, List.of(7L, 8L)));
        doThrow(new CustomException("제공 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)).when(transport).deleteDelivery(9L);
        mvc.perform(delete("/api/v1/lab/guide-deliveries/9")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/lab/guide-deliveries/2")).andExpect(status().isNoContent());
    }

    @Test
    void policyDocShapes() throws Exception {
        when(policy.list()).thenReturn(List.of(new LabPolicyDocDto.View(1L, "CARE_GUIDE", 10, "개인정보보호지침",
                LocalDate.of(2026, 1, 5), "서류함", Map.of("purpose", true), LocalDate.of(2026, 2, 1), null, null)));
        mvc.perform(get("/api/v1/lab/policy-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].guideNo").value(10))
                .andExpect(jsonPath("$[0].revisedOn").exists())
                .andExpect(jsonPath("$[0].location").value("서류함"))
                .andExpect(jsonPath("$[0].checks.purpose").value(true))
                .andExpect(jsonPath("$[0].trainedOn").exists());
        when(policy.create(any())).thenThrow(new CustomException("같은 종류의 문서가 이미 대장에 있습니다", HttpStatus.CONFLICT));
        mvc.perform(post("/api/v1/lab/policy-docs").contentType(MediaType.APPLICATION_JSON).content(
                "{\"kind\":\"OPERATING_RULES\",\"title\":\"운영규정\",\"checks\":{\"capacity\":true}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("같은 종류의 문서가 이미 대장에 있습니다"));
        verify(policy).create(new LabPolicyDocDto.Input("OPERATING_RULES", null, "운영규정", null, null,
                Map.of("capacity", true), null, null));
        mvc.perform(put("/api/v1/lab/policy-docs/1").contentType(MediaType.APPLICATION_JSON).content(
                "{\"kind\":\"OTHER\",\"title\":\"x\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/lab/policy-docs/1")).andExpect(status().isNoContent());
        verify(policy).delete(1L);
    }
}
