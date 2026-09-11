package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.CareGrade;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.CognitionLevel;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.DiaperType;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.Gender;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.MealType;
import com.silverithm.vehicleplacementsystem.service.ElderCareProfileSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 케어 정보 응답.
 *
 * <p>주민번호는 절대 원문으로 나가지 않는다 — 목록·상세 모두 마스킹 값만 싣고,
 * 전체 값은 관리자 전용 단건 조회에서만 열린다.
 */
public record ElderCareProfileDTO(
        String residentNumberMasked,
        LocalDate birthDate,
        Gender gender,
        Integer age,
        CareGrade careGrade,
        boolean fallRisk,
        String fallNote,
        boolean pressureSore,
        String pressureSoreNote,
        DiaperType diaperType,
        boolean diaperIntermittent,
        CognitionLevel cognitionLevel,
        String cognitionNote,
        MealType mealType,
        boolean morningSnack,
        boolean afternoonSnack,
        boolean dinner,
        String mealNote,
        String bathTime,
        String bathNote,
        boolean medMorning,
        boolean medLunch,
        boolean medEvening,
        String medMorningTime,
        String medLunchTime,
        String medEveningTime,
        String medNote,
        String vehicleNote,
        Integer floor,
        String seatNote,
        String careNote,
        LocalDateTime updatedAt
) {

    public static ElderCareProfileDTO from(ElderCareProfile profile) {
        if (profile == null) {
            return null;
        }
        return new ElderCareProfileDTO(
                ElderCareProfileSupport.mask(profile.getResidentNumber()),
                profile.getBirthDate(),
                profile.getGender(),
                ElderCareProfileSupport.ageOf(profile.getBirthDate()),
                profile.getCareGrade(),
                profile.isFallRisk(),
                profile.getFallNote(),
                profile.isPressureSore(),
                profile.getPressureSoreNote(),
                profile.getDiaperType(),
                profile.isDiaperIntermittent(),
                profile.getCognitionLevel(),
                profile.getCognitionNote(),
                profile.getMealType(),
                profile.isMorningSnack(),
                profile.isAfternoonSnack(),
                profile.isDinner(),
                profile.getMealNote(),
                profile.getBathTime(),
                profile.getBathNote(),
                profile.isMedMorning(),
                profile.isMedLunch(),
                profile.isMedEvening(),
                profile.getMedMorningTime(),
                profile.getMedLunchTime(),
                profile.getMedEveningTime(),
                profile.getMedNote(),
                profile.getVehicleNote(),
                profile.getFloor(),
                profile.getSeatNote(),
                profile.getCareNote(),
                profile.getModifiedAt());
    }
}
