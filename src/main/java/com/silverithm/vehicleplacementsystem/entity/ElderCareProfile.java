package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.ElderCareProfileRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어르신 케어 정보 — 요양보호사가 매일 보는 돌봄 조건(식사·투약·목욕·자리·인지·낙상 등).
 *
 * <p>어르신 본체(Elderly)와 1:1이며 PK를 공유한다({@code @MapsId}). 배차 코드가 Elderly를
 * 널리 조회하는데 이 표는 훨씬 무겁고 민감해서, 본체에 컬럼을 더하는 대신 표를 나눴다.
 *
 * <p>주민번호와 메모 계열은 {@link EncryptedPiiConverter}로 컬럼 암호화한다. 암호문이
 * 평문의 약 4.2배가 되므로 컬럼은 평문 500자 기준 2048자로 잡는다.
 */
@Entity
@Table(name = "elder_care_profile")
@Getter
@NoArgsConstructor
public class ElderCareProfile extends BaseEntity {

    @Id
    @Column(name = "elderly_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "elderly_id")
    private Elderly elderly;

    /** 주민등록번호 13자리(하이픈 없이). 암호화 저장이라 DB에서 검색·정렬할 수 없다. */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "resident_number", length = 2048)
    private String residentNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_grade", length = 20)
    private CareGrade careGrade;

    @Column(name = "fall_risk", nullable = false)
    private boolean fallRisk;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "fall_note", length = 2048)
    private String fallNote;

    @Column(name = "pressure_sore", nullable = false)
    private boolean pressureSore;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "pressure_sore_note", length = 2048)
    private String pressureSoreNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "diaper_type", length = 20)
    private DiaperType diaperType;

    /** 늘 차는 게 아니라 필요할 때만 쓰는 경우 */
    @Column(name = "diaper_intermittent", nullable = false)
    private boolean diaperIntermittent;

    @Enumerated(EnumType.STRING)
    @Column(name = "cognition_level", length = 20)
    private CognitionLevel cognitionLevel;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "cognition_note", length = 2048)
    private String cognitionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 20)
    private MealType mealType;

    /** 간식·저녁은 대부분 드시므로 기본이 true다 — 안 드시는 분만 꺼 둔다 */
    @Column(name = "morning_snack", nullable = false)
    private boolean morningSnack = true;

    @Column(name = "afternoon_snack", nullable = false)
    private boolean afternoonSnack = true;

    @Column(name = "dinner", nullable = false)
    private boolean dinner = true;

    /** 기피식품·대체식품·기간 조건 */
    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "meal_note", length = 2048)
    private String mealNote;

    /** 예 "9:40-50" — 시각이 아니라 순번 표기라 문자열로 둔다 */
    @Column(name = "bath_time", length = 50)
    private String bathTime;

    @Column(name = "bath_note", length = 255)
    private String bathNote;

    @Column(name = "med_morning", nullable = false)
    private boolean medMorning;

    @Column(name = "med_lunch", nullable = false)
    private boolean medLunch;

    @Column(name = "med_evening", nullable = false)
    private boolean medEvening;

    @Column(name = "med_morning_time", length = 20)
    private String medMorningTime;

    @Column(name = "med_lunch_time", length = 20)
    private String medLunchTime;

    @Column(name = "med_evening_time", length = 20)
    private String medEveningTime;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "med_note", length = 2048)
    private String medNote;

    /** 이용 차량·등하원 특이사항 */
    @Column(name = "vehicle_note", length = 255)
    private String vehicleNote;

    /** 자리 층(1/2…) */
    @Column(name = "floor")
    private Integer floor;

    @Column(name = "seat_note", length = 100)
    private String seatNote;

    @Convert(converter = EncryptedPiiConverter.class)
    @Column(name = "care_note", length = 2048)
    private String careNote;

    public ElderCareProfile(Elderly elderly) {
        this.elderly = elderly;
    }

    /**
     * 요청 객체로 전체를 덮어쓴다.
     *
     * <p>주민번호만 예외다 — 화면은 마스킹된 값만 받아 가므로 그대로 되돌려보낼 수 없다.
     * 그래서 null은 "건드리지 않음", 빈 문자열은 "지움"으로 호출자가 정리해 넘기고,
     * 그 판단 결과를 {@code residentNumberTouched}로 알려 준다.
     *
     * @param values             정규화가 끝난 요청(주민번호는 하이픈 제거, 생년월일·성별은 파생 반영)
     * @param residentNumberTouched 주민번호 칸을 실제로 바꾸는 요청인지
     */
    public void apply(ElderCareProfileRequest values, boolean residentNumberTouched) {
        apply(values, residentNumberTouched, false);
    }

    /**
     * 케어 정보를 반영한다.
     *
     * @param merge 참이면 <b>채우기</b> — 값이 없는 칸은 저장된 값을 그대로 둔다.
     *              거짓이면 <b>덮어쓰기</b> — 요청이 그 어르신의 케어 정보 전체가 된다.
     *
     * <p>화면 수정은 덮어쓰기다. 스위치를 끄거나 메모를 지운 것이 저장돼야 하기 때문이다.
     * 엑셀 업로드는 채우기다. 현장 명단은 시트마다 담는 항목이 달라서, 자리 시트만 올렸다고
     * 투약이 지워지면 안 된다. 지워진 투약은 화면에서 '미입력'으로만 보여 알아채기 어렵다.
     */
    public void apply(ElderCareProfileRequest values, boolean residentNumberTouched, boolean merge) {
        if (residentNumberTouched) {
            this.residentNumber = values.residentNumber();
        }
        this.birthDate = pick(values.birthDate(), this.birthDate, merge);
        this.gender = pick(values.gender(), this.gender, merge);
        this.careGrade = pick(values.careGrade(), this.careGrade, merge);
        this.fallRisk = flag(values.fallRisk(), this.fallRisk, merge);
        this.fallNote = pick(values.fallNote(), this.fallNote, merge);
        this.pressureSore = flag(values.pressureSore(), this.pressureSore, merge);
        this.pressureSoreNote = pick(values.pressureSoreNote(), this.pressureSoreNote, merge);
        this.diaperType = pick(values.diaperType(), this.diaperType, merge);
        this.diaperIntermittent = flag(values.diaperIntermittent(), this.diaperIntermittent, merge);
        this.cognitionLevel = pick(values.cognitionLevel(), this.cognitionLevel, merge);
        this.cognitionNote = pick(values.cognitionNote(), this.cognitionNote, merge);
        this.mealType = pick(values.mealType(), this.mealType, merge);
        // 간식·저녁은 덮어쓰기에서 값이 없으면 true다 — 대부분 드시는 쪽이 기본이라
        // 누락을 "안 드심"으로 읽으면 안 된다. 채우기에서는 저장된 값을 그대로 둔다.
        this.morningSnack = merge ? flag(values.morningSnack(), this.morningSnack, true)
                : !Boolean.FALSE.equals(values.morningSnack());
        this.afternoonSnack = merge ? flag(values.afternoonSnack(), this.afternoonSnack, true)
                : !Boolean.FALSE.equals(values.afternoonSnack());
        this.dinner = merge ? flag(values.dinner(), this.dinner, true)
                : !Boolean.FALSE.equals(values.dinner());
        this.mealNote = pick(values.mealNote(), this.mealNote, merge);
        this.bathTime = pick(values.bathTime(), this.bathTime, merge);
        this.bathNote = pick(values.bathNote(), this.bathNote, merge);
        this.medMorning = flag(values.medMorning(), this.medMorning, merge);
        this.medLunch = flag(values.medLunch(), this.medLunch, merge);
        this.medEvening = flag(values.medEvening(), this.medEvening, merge);
        this.medMorningTime = pick(values.medMorningTime(), this.medMorningTime, merge);
        this.medLunchTime = pick(values.medLunchTime(), this.medLunchTime, merge);
        this.medEveningTime = pick(values.medEveningTime(), this.medEveningTime, merge);
        this.medNote = pick(values.medNote(), this.medNote, merge);
        this.vehicleNote = pick(values.vehicleNote(), this.vehicleNote, merge);
        this.floor = pick(values.floor(), this.floor, merge);
        this.seatNote = pick(values.seatNote(), this.seatNote, merge);
        this.careNote = pick(values.careNote(), this.careNote, merge);
    }

    /** 채우기에서 값이 없으면 저장된 값을 그대로 둔다 */
    private static <T> T pick(T incoming, T current, boolean merge) {
        return (merge && incoming == null) ? current : incoming;
    }

    /** 켬/끔은 안 보낸 것(null)과 끈 것(false)이 다르다 — 채우기에서 null은 그대로 둔다 */
    private static boolean flag(Boolean incoming, boolean current, boolean merge) {
        if (incoming != null) {
            return incoming;
        }
        return merge ? current : false;
    }

    /** 저장된 주민번호가 있는지 */
    public boolean hasResidentNumber() {
        return residentNumber != null && !residentNumber.isBlank();
    }

    public enum Gender {
        MALE, FEMALE
    }

    /** 장기요양등급 — 등급 외(인지지원등급)와 미판정(NONE)이 실제로 섞여 있다 */
    public enum CareGrade {
        GRADE_1, GRADE_2, GRADE_3, GRADE_4, GRADE_5, COGNITIVE_SUPPORT, NONE
    }

    /** 팬티기저귀/패드/둘 다 */
    public enum DiaperType {
        NONE, PANTY, PAD, BOTH
    }

    public enum CognitionLevel {
        NORMAL, MILD, MODERATE, SEVERE
    }

    /** 일반식/다진식/죽/비빔식 */
    public enum MealType {
        REGULAR, CHOPPED, PORRIDGE, MIXED
    }
}
