package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.CareGrade;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.CognitionLevel;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.DiaperType;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.Gender;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.MealType;
import java.time.LocalDate;

/**
 * 케어 정보 저장 요청.
 *
 * <p>bool 계열을 원시 타입이 아니라 {@code Boolean}으로 받는다 — 앱이 보낸 적 없는 칸과
 * 일부러 끈 칸을 구분해야 간식·저녁의 기본값 true를 지킬 수 있다.
 *
 * <p>{@code residentNumber}는 응답에 마스킹만 실리므로 값이 없다고 지우면 안 된다.
 * null/미포함은 "기존 값 유지", 빈 문자열은 "삭제"다.
 */
public record ElderCareProfileRequest(
        String residentNumber,
        LocalDate birthDate,
        Gender gender,
        CareGrade careGrade,
        Boolean fallRisk,
        String fallNote,
        Boolean pressureSore,
        String pressureSoreNote,
        DiaperType diaperType,
        Boolean diaperIntermittent,
        CognitionLevel cognitionLevel,
        String cognitionNote,
        MealType mealType,
        Boolean morningSnack,
        Boolean afternoonSnack,
        Boolean dinner,
        String mealNote,
        String bathTime,
        String bathNote,
        Boolean medMorning,
        Boolean medLunch,
        Boolean medEvening,
        String medMorningTime,
        String medLunchTime,
        String medEveningTime,
        String medNote,
        String vehicleNote,
        Integer floor,
        String seatNote,
        String careNote
) {

    /** 주민번호·생년월일·성별만 갈아 끼운 사본 (정규화·파생 결과를 담는다) */
    public ElderCareProfileRequest withIdentity(String residentNumber, LocalDate birthDate, Gender gender) {
        return new ElderCareProfileRequest(residentNumber, birthDate, gender, careGrade, fallRisk, fallNote,
                pressureSore, pressureSoreNote, diaperType, diaperIntermittent, cognitionLevel, cognitionNote,
                mealType, morningSnack, afternoonSnack, dinner, mealNote, bathTime, bathNote,
                medMorning, medLunch, medEvening, medMorningTime, medLunchTime, medEveningTime, medNote,
                vehicleNote, floor, seatNote, careNote);
    }
}
