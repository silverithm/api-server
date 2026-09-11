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
        if (residentNumberTouched) {
            this.residentNumber = values.residentNumber();
        }
        this.birthDate = values.birthDate();
        this.gender = values.gender();
        this.careGrade = values.careGrade();
        this.fallRisk = Boolean.TRUE.equals(values.fallRisk());
        this.fallNote = values.fallNote();
        this.pressureSore = Boolean.TRUE.equals(values.pressureSore());
        this.pressureSoreNote = values.pressureSoreNote();
        this.diaperType = values.diaperType();
        this.diaperIntermittent = Boolean.TRUE.equals(values.diaperIntermittent());
        this.cognitionLevel = values.cognitionLevel();
        this.cognitionNote = values.cognitionNote();
        this.mealType = values.mealType();
        // 간식·저녁은 값이 없으면 true다 — 대부분 드시는 쪽이 기본이라 누락을 "안 드심"으로 읽으면 안 된다
        this.morningSnack = !Boolean.FALSE.equals(values.morningSnack());
        this.afternoonSnack = !Boolean.FALSE.equals(values.afternoonSnack());
        this.dinner = !Boolean.FALSE.equals(values.dinner());
        this.mealNote = values.mealNote();
        this.bathTime = values.bathTime();
        this.bathNote = values.bathNote();
        this.medMorning = Boolean.TRUE.equals(values.medMorning());
        this.medLunch = Boolean.TRUE.equals(values.medLunch());
        this.medEvening = Boolean.TRUE.equals(values.medEvening());
        this.medMorningTime = values.medMorningTime();
        this.medLunchTime = values.medLunchTime();
        this.medEveningTime = values.medEveningTime();
        this.medNote = values.medNote();
        this.vehicleNote = values.vehicleNote();
        this.floor = values.floor();
        this.seatNote = values.seatNote();
        this.careNote = values.careNote();
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
