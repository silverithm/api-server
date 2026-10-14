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
import com.silverithm.vehicleplacementsystem.lab.dto.LabClaimReviewDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicationDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicineDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabBathLogService;
import com.silverithm.vehicleplacementsystem.lab.service.LabClaimReviewService;
import com.silverithm.vehicleplacementsystem.lab.service.LabDischargeReferralService;
import com.silverithm.vehicleplacementsystem.lab.service.LabElderContractService;
import com.silverithm.vehicleplacementsystem.lab.service.LabMedicationService;
import com.silverithm.vehicleplacementsystem.lab.service.LabMedicineService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 6차 컨트롤러 — 경로·JSON 필드 이름·오류 형식(400/409/204)이 계약대로 나간다. */
class LabSixthControllerTest {

    private final LabMedicationService medication = mock(LabMedicationService.class);
    private final LabMedicineService medicine = mock(LabMedicineService.class);
    private final LabBathLogService bath = mock(LabBathLogService.class);
    private final LabElderContractService contracts = mock(LabElderContractService.class);
    private final LabDischargeReferralService referrals = mock(LabDischargeReferralService.class);
    private final LabClaimReviewService claims = mock(LabClaimReviewService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LabMedicationRequestController(medication),
                        new LabMedicationDoseController(medication), new LabMedicineStockController(medicine),
                        new LabMedicineCheckController(medicine), new LabBathLogController(bath),
                        new LabElderContractController(contracts), new LabDischargeReferralController(referrals),
                        new LabClaimReviewController(claims))
                .setControllerAdvice(new LabExceptionHandler())
                .build();
    }

    // 투약의뢰서 응답의 필드 이름과 약 한 개의 모양(name, dose, route, slots, timing, note)
    @Test
    void medicationRequestJsonShape() throws Exception {
        when(medication.listRequests("ACTIVE", null)).thenReturn(List.of(new LabMedicationDto.RequestView(1L, 2L, "김순자",
                LocalDate.of(2026, 1, 10), "김철수", "CHILD", List.of(new LabMedicationDto.MedicationItem("혈압약", "1정",
                "ORAL", List.of("MORNING", "BEDTIME"), "AFTER_MEAL", "물과 함께")), LocalDate.of(2026, 1, 10), null, true,
                "ACTIVE", null, "박간호사", false, null)));
        mvc.perform(get("/api/v1/lab/medication-requests").param("status", "ACTIVE")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].elderlyName").value("김순자"))
                .andExpect(jsonPath("$[0].requesterRelation").value("CHILD"))
                .andExpect(jsonPath("$[0].prescriptionChecked").value(true))
                .andExpect(jsonPath("$[0].medicines[0].name").value("혈압약"))
                .andExpect(jsonPath("$[0].medicines[0].dose").value("1정"))
                .andExpect(jsonPath("$[0].medicines[0].route").value("ORAL"))
                .andExpect(jsonPath("$[0].medicines[0].slots[1]").value("BEDTIME"))
                .andExpect(jsonPath("$[0].medicines[0].timing").value("AFTER_MEAL"))
                .andExpect(jsonPath("$[0].medicines[0].note").value("물과 함께"))
                .andExpect(jsonPath("$[0].writerName").value("박간호사"));
    }

    // 투약 기록이 있는 의뢰서 삭제는 409 {"message"}, 정상 삭제는 204
    @Test
    void medicationDeleteConflictAndNoContent() throws Exception {
        org.mockito.Mockito.doThrow(new CustomException("투약 기록이 있는 의뢰서는 삭제할 수 없습니다. 끝내기로 바꿔 주세요",
                HttpStatus.CONFLICT)).when(medication).deleteRequest(7L);
        mvc.perform(delete("/api/v1/lab/medication-requests/7")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("투약 기록이 있는 의뢰서는 삭제할 수 없습니다. 끝내기로 바꿔 주세요"));
        mvc.perform(delete("/api/v1/lab/medication-requests/8")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/lab/medication-doses/3")).andExpect(status().isNoContent());
        verify(medication).deleteDose(3L);
    }

    // 투약 기록·점검·청구 대조 upsert는 PUT, 본문 필드 이름이 그대로 서비스 입력으로 들어간다
    @Test
    void putUpsertsBindBodies() throws Exception {
        when(medication.upsertDose(any())).thenReturn(new LabMedicationDto.DoseView(1L, 2L, "김순자", 3L,
                LocalDate.of(2026, 1, 10), "MORNING", "GIVEN", "08:30", "이요양", null, null));
        mvc.perform(put("/api/v1/lab/medication-doses").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"elderlyId\":2,\"requestId\":3,\"givenOn\":\"2026-01-10\",\"slot\":\"MORNING\",\"status\":\"GIVEN\","
                                + "\"givenAt\":\"08:30\",\"giverName\":\"이요양\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.givenAt").value("08:30"))
                .andExpect(jsonPath("$.requestId").value(3));
        when(medicine.upsertCheck(any())).thenReturn(new LabMedicineDto.CheckView(1L, LocalDate.of(2026, 3, 5), "박간호사",
                true, null, List.of(new LabMedicineDto.CheckItem(9L, true, "DISPOSE", null)), null, null));
        mvc.perform(put("/api/v1/lab/medicine-checks").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"checkedOn\":\"2026-03-05\",\"inspectorName\":\"박간호사\",\"cabinetLocked\":true,"
                                + "\"items\":[{\"stockId\":9,\"ok\":true,\"action\":\"DISPOSE\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].stockId").value(9))
                .andExpect(jsonPath("$.items[0].action").value("DISPOSE"))
                .andExpect(jsonPath("$.cabinetLocked").value(true));
    }

    // 약품 목록은 기본이 폐기 제외, includeDisposed=true면 포함
    @Test
    void stockIncludeDisposedParam() throws Exception {
        when(medicine.listStock(false)).thenReturn(List.of());
        when(medicine.listStock(true)).thenReturn(List.of(new LabMedicineDto.StockView(1L, "소화제", "소화 불량", 5, "정",
                "ROOM", LocalDate.of(2026, 6, 1), null, LocalDate.of(2026, 2, 1), null, null)));
        mvc.perform(get("/api/v1/lab/medicine-stock")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/lab/medicine-stock").param("includeDisposed", "true")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("소화제"))
                .andExpect(jsonPath("$[0].efficacy").value("소화 불량"));
    }

    // 둘째 진행 중 계약은 409, 퇴소 제공대장 네 칸 위반은 400 — 메시지가 그대로 나간다
    @Test
    void contractConflictAndReferralBadRequest() throws Exception {
        when(contracts.create(any())).thenThrow(new CustomException("이 어르신의 진행 중인 계약이 이미 있습니다",
                HttpStatus.CONFLICT));
        mvc.perform(post("/api/v1/lab/elder-contracts").contentType(MediaType.APPLICATION_JSON).content("{\"elderlyId\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이 어르신의 진행 중인 계약이 이미 있습니다"));
        when(referrals.create(any())).thenThrow(new CustomException(
                "제공대장 네 칸(제공일자·방법·제공자·수령자)을 함께 적어 주세요", HttpStatus.BAD_REQUEST));
        mvc.perform(post("/api/v1/lab/discharge-referrals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderlyId\":2,\"providedOn\":\"2026-05-19\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("제공대장 네 칸(제공일자·방법·제공자·수령자)을 함께 적어 주세요"));
    }

    // 청구 대조: PUT /claim-reviews/batch가 PUT /claim-reviews와 따로 매핑된다
    @Test
    void claimReviewBatchPath() throws Exception {
        when(claims.batch(any())).thenReturn(List.of(new LabClaimReviewDto.View(1L, 2L, "김순자", "2026-03", 22, 20, null,
                "h8to10", "MISMATCH", null, "박사회복지사", null)));
        mvc.perform(put("/api/v1/lab/claim-reviews/batch").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"month\":\"2026-03\",\"rows\":[{\"elderlyId\":2,\"claimMonth\":\"2026-03\",\"claimedDays\":22,"
                                + "\"attendedDays\":20}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("MISMATCH"))
                .andExpect(jsonPath("$[0].claimedDays").value(22)).andExpect(jsonPath("$[0].attendedDays").value(20))
                .andExpect(jsonPath("$[0].claimedBand").value("h8to10"));
        verify(claims).batch(any());
        when(claims.upsert(any())).thenReturn(new LabClaimReviewDto.View(1L, 2L, "김순자", "2026-03", 20, 20, 20, null,
                "MATCH", null, null, null));
        mvc.perform(put("/api/v1/lab/claim-reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderlyId\":2,\"claimMonth\":\"2026-03\",\"claimedDays\":20,\"attendedDays\":20,"
                                + "\"billedDays\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.billedDays").value(20));
        mvc.perform(delete("/api/v1/lab/claim-reviews/1")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/lab/bath-logs").param("month", "2026-03")).andExpect(status().isOk());
        verify(bath).list("2026-03", null);
        // 날짜 형식이 틀린 본문은 500이 아니라 400
        mvc.perform(post("/api/v1/lab/bath-logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elderlyId\":2,\"bathDate\":\"내일\"}")).andExpect(status().isBadRequest());
    }
}
