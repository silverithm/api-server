package com.silverithm.vehicleplacementsystem.lab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareRecordDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabFacilityDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabCareRecordService;
import com.silverithm.vehicleplacementsystem.lab.service.LabFacilityService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 12차 컨트롤러 — 경로·JSON 필드 이름·오류 형식(400/404/204)과 서식 칸 JSON 읽기가 계약대로 나간다. */
class LabTwelfthControllerTest {

    private final LabFacilityService facility = mock(LabFacilityService.class);
    private final LabCareRecordService careRecords = mock(LabCareRecordService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabFacilityController(facility),
                        new LabCareRecordController(careRecords))
                .setControllerAdvice(new LabExceptionHandler()).build();
    }

    @Test
    void facilityProfileShapes() throws Exception {
        when(facility.getProfile()).thenReturn(new LabFacilityDto.ProfileView(null, null, null, "UNKNOWN", "UNKNOWN",
                "UNKNOWN", null, "UNKNOWN", "UNKNOWN", "UNKNOWN", new LinkedHashMap<>(), null, null, null, null));
        mvc.perform(get("/api/v1/lab/facility/profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isEmpty()).andExpect(jsonPath("$.sprinkler").value("UNKNOWN"))
                .andExpect(jsonPath("$.appliesOverride").isMap())
                .andExpect(jsonPath("$.lpgCapacityKg").isEmpty());

        when(facility.saveProfile(any())).thenThrow(new CustomException("적용 여부는 YES 또는 NO만 지정할 수 있습니다",
                HttpStatus.BAD_REQUEST));
        mvc.perform(put("/api/v1/lab/facility/profile").contentType(MediaType.APPLICATION_JSON).content(
                "{\"approvedOn\":\"2012-05-20\",\"grossAreaM2\":850,\"gasKind\":\"LPG\",\"lpgCapacityKg\":300,"
                        + "\"appliesOverride\":{\"ELEV_ANNUAL\":\"MAYBE\"},\"fireManagerName\":\"김안전\","
                        + "\"unknownKey\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("적용 여부는 YES 또는 NO만 지정할 수 있습니다"));
        verify(facility).saveProfile(new LabFacilityDto.ProfileInput(LocalDate.of(2012, 5, 20), 850, null, null, "LPG",
                300, null, null, null, Map.of("ELEV_ANNUAL", "MAYBE"), "김안전", null));
    }

    @Test
    void facilityLogShapes() throws Exception {
        when(facility.listLogs("2026", null)).thenReturn(List.of(new LabFacilityDto.LogView(5L, "FIRE_OPERATION",
                LocalDate.of(2026, 4, 3), "VENDOR", "대한소방", "PASS", null, LocalDate.of(2026, 4, 10), null, "서류함",
                "원장", null)));
        mvc.perform(get("/api/v1/lab/facility/logs").param("year", "2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemCode").value("FIRE_OPERATION"))
                .andExpect(jsonPath("$[0].doneOn").exists())
                .andExpect(jsonPath("$[0].reportedOn").exists())
                .andExpect(jsonPath("$[0].validUntil").isEmpty())
                .andExpect(jsonPath("$[0].writerName").value("원장"));
        mvc.perform(get("/api/v1/lab/facility/logs").param("itemCode", "ELEV_ANNUAL")).andExpect(status().isOk());
        verify(facility).listLogs(null, "ELEV_ANNUAL");

        when(facility.createLog(any())).thenThrow(new CustomException("보험 만기일을(를) 입력해 주세요", HttpStatus.BAD_REQUEST));
        mvc.perform(post("/api/v1/lab/facility/logs").contentType(MediaType.APPLICATION_JSON).content(
                "{\"itemCode\":\"INSURANCE_FACILITY\",\"doneOn\":\"2026-03-01\",\"doneBy\":\"VENDOR\","
                        + "\"vendorName\":\"보험사\",\"result\":\"PASS\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("보험 만기일을(를) 입력해 주세요"));
        verify(facility).createLog(new LabFacilityDto.LogInput("INSURANCE_FACILITY", LocalDate.of(2026, 3, 1), "VENDOR",
                "보험사", "PASS", null, null, null, null));
        when(facility.updateLog(eq(9L), any())).thenThrow(new CustomException("시설 점검 기록을 찾을 수 없습니다",
                HttpStatus.NOT_FOUND));
        mvc.perform(put("/api/v1/lab/facility/logs/9").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("시설 점검 기록을 찾을 수 없습니다"));
        mvc.perform(delete("/api/v1/lab/facility/logs/3")).andExpect(status().isNoContent());
        verify(facility).deleteLog(3L);
    }

    // 서식 칸 JSON을 읽을 때 모르는 키는 버려지고 숫자·맵이 계약 모양으로 들어온다
    @Test
    void careRecordFormShapes() throws Exception {
        when(careRecords.upsert(any())).thenReturn(null);
        mvc.perform(put("/api/v1/lab/care-records").contentType(MediaType.APPLICATION_JSON).content(
                "{\"elderlyId\":1,\"recordDate\":\"2026-10-14\",\"form\":{\"startTime\":\"09:00\",\"temperature\":36.5,"
                        + "\"bpSystolic\":120,\"bpDiastolic\":80,\"washHelp\":true,\"writers\":{\"PHYSICAL\":7,"
                        + "\"MARS\":9},\"reasons\":[{\"kind\":\"UNUSED_DAY\",\"text\":\"입원\",\"extra\":1}],"
                        + "\"somethingNew\":\"x\"}}")).andExpect(status().isOk());
        ArgumentCaptor<LabCareRecordDto.Input> captor = ArgumentCaptor.forClass(LabCareRecordDto.Input.class);
        verify(careRecords).upsert(captor.capture());
        LabCareRecordDto.Form form = captor.getValue().form();
        assertThat(form.startTime()).isEqualTo("09:00");
        assertThat(form.temperature()).isEqualByComparingTo("36.5");
        assertThat(form.washHelp()).isTrue();
        assertThat(form.writers()).containsEntry("PHYSICAL", 7L);
        assertThat(form.reasons()).containsExactly(new LabCareRecordDto.Reason("UNUSED_DAY", "입원"));

        Map<String, Long> writers = new LinkedHashMap<>();
        writers.put("PHYSICAL", 7L);
        Map<String, String> names = new LinkedHashMap<>();
        names.put("PHYSICAL", "이요양");
        when(careRecords.listByDate("2026-10-14")).thenReturn(List.of(new LabCareRecordDto.View(1L, 1L, "김순자",
                LocalDate.of(2026, 10, 14), null, null, null, null, null, null, null, null, null, null, false, null,
                new LabCareRecordDto.Form("09:00", null, false, null, true, null, null, null, null, null, false, false,
                        false, null, null, new java.math.BigDecimal("36.5"), null, null, false, null, false, false,
                        false, false, writers, names, List.of()))));
        mvc.perform(get("/api/v1/lab/care-records").param("date", "2026-10-14")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].form.writerNames.PHYSICAL").value("이요양"))
                .andExpect(jsonPath("$[0].form.temperature").value(36.5))
                .andExpect(jsonPath("$[0].form.writers.PHYSICAL").value(7))
                .andExpect(jsonPath("$[0].form.reasons").isArray());
    }
}
