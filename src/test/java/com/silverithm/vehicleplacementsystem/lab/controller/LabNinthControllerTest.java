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
import com.silverithm.vehicleplacementsystem.lab.dto.LabBankDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffHrDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabBankService;
import com.silverithm.vehicleplacementsystem.lab.service.LabStaffHrService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 9차 컨트롤러 — 경로·JSON 필드 이름·오류 형식(400/404/409/204)이 계약대로 나간다. */
class LabNinthControllerTest {

    private final LabStaffHrService hr = mock(LabStaffHrService.class);
    private final LabBankService bank = mock(LabBankService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabStaffProfileController(hr), new LabStaffCertController(hr),
                        new LabBankAccountController(bank), new LabBankStatementController(bank))
                .setControllerAdvice(new LabExceptionHandler()).build();
    }

    @Test
    void staffShapes() throws Exception {
        when(hr.listProfiles()).thenReturn(List.of(new LabStaffHrDto.ProfileView(1L, 7L, "이요양",
                LocalDate.of(2024, 3, 1), "CAREGIVER", 1980, LocalDate.of(2023, 11, 20), true, "메모", null)));
        mvc.perform(get("/api/v1/lab/staff-profiles")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(7))
                .andExpect(jsonPath("$[0].memberName").value("이요양"))
                .andExpect(jsonPath("$[0].hireDate").exists())
                .andExpect(jsonPath("$[0].jobType").value("CAREGIVER"))
                .andExpect(jsonPath("$[0].birthYear").value(1980))
                .andExpect(jsonPath("$[0].licensePassedOn").exists())
                .andExpect(jsonPath("$[0].officeWorker").value(true));
        when(hr.upsertProfile(any())).thenReturn(new LabStaffHrDto.ProfileView(1L, 7L, "이요양",
                LocalDate.of(2024, 3, 1), "NURSE", null, null, false, null, null));
        mvc.perform(put("/api/v1/lab/staff-profiles").contentType(MediaType.APPLICATION_JSON).content(
                "{\"memberId\":7,\"hireDate\":\"2024-03-01\",\"jobType\":\"NURSE\",\"birthYear\":null,"
                        + "\"officeWorker\":false}")).andExpect(status().isOk());
        verify(hr).upsertProfile(new LabStaffHrDto.ProfileInput(7L, LocalDate.of(2024, 3, 1), "NURSE", null, null,
                false, null));
        mvc.perform(delete("/api/v1/lab/staff-profiles/3")).andExpect(status().isNoContent());
        verify(hr).deleteProfile(3L);

        when(hr.listCerts(7L, null)).thenReturn(List.of(new LabStaffHrDto.CertView(2L, 7L, "이요양",
                "CONTINUING_EDU", LocalDate.of(2025, 6, 1), new BigDecimal("8.5"), "교육원", "A-1", null, null)));
        mvc.perform(get("/api/v1/lab/staff-certs").param("memberId", "7")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("CONTINUING_EDU"))
                .andExpect(jsonPath("$[0].doneOn").exists())
                .andExpect(jsonPath("$[0].hours").value(8.5))
                .andExpect(jsonPath("$[0].certNo").value("A-1"))
                .andExpect(jsonPath("$[0].memberName").value("이요양"));
        when(hr.listCerts(null, null)).thenThrow(new CustomException("memberId 또는 year 중 하나를 보내 주세요",
                HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/v1/lab/staff-certs")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("memberId 또는 year 중 하나를 보내 주세요"));
        when(hr.createCert(any())).thenThrow(new CustomException("직원을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        mvc.perform(post("/api/v1/lab/staff-certs").contentType(MediaType.APPLICATION_JSON).content(
                "{\"memberId\":9,\"kind\":\"HEALTH_CHECK\",\"doneOn\":\"2026-01-05\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/lab/staff-certs/5")).andExpect(status().isNoContent());
        verify(hr).deleteCert(5L);
    }

    @Test
    void bankShapes() throws Exception {
        when(bank.listAccounts()).thenReturn(List.of(new LabBankDto.AccountView(1L, "운영 통장", "OPERATING", "농협",
                "1234", 1_000_000L, LocalDate.of(2026, 1, 1), true, null)));
        mvc.perform(get("/api/v1/lab/bank-accounts")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountTail").value("1234"))
                .andExpect(jsonPath("$[0].openingBalance").value(1000000))
                .andExpect(jsonPath("$[0].bankName").value("농협"))
                .andExpect(jsonPath("$[0].active").value(true));
        when(bank.createAccount(any())).thenThrow(new CustomException("끝 4자리", HttpStatus.BAD_REQUEST));
        mvc.perform(post("/api/v1/lab/bank-accounts").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\":\"x\",\"purpose\":\"OTHER\",\"bankName\":\"농협\",\"accountTail\":\"110-1\","
                        + "\"openingBalance\":0,\"openingDate\":\"2026-01-01\"}")).andExpect(status().isBadRequest());
        org.mockito.Mockito.doThrow(new CustomException("월말 잔액 기록이 있는 통장은 지울 수 없습니다", HttpStatus.CONFLICT))
                .when(bank).deleteAccount(4L);
        mvc.perform(delete("/api/v1/lab/bank-accounts/4")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("월말 잔액 기록이 있는 통장은 지울 수 없습니다"));

        when(bank.listStatements("2026-03", null)).thenReturn(List.of(new LabBankDto.StatementView(2L, 1L,
                "운영 통장", "2026-03", 5_000_000L, null, null)));
        mvc.perform(get("/api/v1/lab/bank-statements").param("month", "2026-03")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountName").value("운영 통장"))
                .andExpect(jsonPath("$[0].statementMonth").value("2026-03"))
                .andExpect(jsonPath("$[0].closingBalance").value(5000000));
        when(bank.listStatements(null, null)).thenThrow(new CustomException("month 또는 accountId", HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/v1/lab/bank-statements")).andExpect(status().isBadRequest());
        when(bank.upsertStatement(any())).thenReturn(new LabBankDto.StatementView(2L, 1L, "운영 통장", "2026-03", 1L,
                null, null));
        mvc.perform(put("/api/v1/lab/bank-statements").contentType(MediaType.APPLICATION_JSON).content(
                "{\"accountId\":1,\"statementMonth\":\"2026-03\",\"closingBalance\":1}")).andExpect(status().isOk());
        verify(bank).upsertStatement(new LabBankDto.StatementInput(1L, "2026-03", 1L, null));
        mvc.perform(delete("/api/v1/lab/bank-statements/2")).andExpect(status().isNoContent());
        verify(bank).deleteStatement(2L);
    }
}
