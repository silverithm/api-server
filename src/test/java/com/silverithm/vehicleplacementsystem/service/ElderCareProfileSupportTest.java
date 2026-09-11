package com.silverithm.vehicleplacementsystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.Gender;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 주민번호 규칙.
 *
 * 여기가 틀리면 화면에 남의 생년월일이 찍히거나, 마스킹했다고 믿은 값이 뒷자리째 나간다.
 * DB 없이 바로 검증되는 순수 규칙이라 경계값을 촘촘히 못박아 둔다.
 */
class ElderCareProfileSupportTest {

    @Test
    @DisplayName("하이픈과 공백을 걷어내고 13자리로 정규화한다")
    void normalizesToThirteenDigits() {
        assertThat(ElderCareProfileSupport.normalizeResidentNumber("410203-2830514")).isEqualTo("4102032830514");
        assertThat(ElderCareProfileSupport.normalizeResidentNumber(" 410203 2830514 ")).isEqualTo("4102032830514");
    }

    @Test
    @DisplayName("null은 '건드리지 않음', 빈 문자열은 '삭제' — 둘을 섞지 않는다")
    void keepsNullAndBlankDistinct() {
        assertThat(ElderCareProfileSupport.normalizeResidentNumber(null)).isNull();
        assertThat(ElderCareProfileSupport.normalizeResidentNumber("")).isEmpty();
        assertThat(ElderCareProfileSupport.normalizeResidentNumber("   ")).isEmpty();
        assertThat(ElderCareProfileSupport.normalizeResidentNumber("-")).isEmpty();
    }

    @Test
    @DisplayName("13자리 숫자가 아니면 400으로 거절한다")
    void rejectsMalformedResidentNumber() {
        assertThatThrownBy(() -> ElderCareProfileSupport.normalizeResidentNumber("41020328305"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> ElderCareProfileSupport.normalizeResidentNumber("410203-283051A"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("마스킹은 앞 6자리와 성별 한 자리만 남긴다")
    void masksAllButGenderDigit() {
        assertThat(ElderCareProfileSupport.mask("4102032830514")).isEqualTo("410203-2******");
        assertThat(ElderCareProfileSupport.mask("410203-2830514")).isEqualTo("410203-2******");
    }

    @Test
    @DisplayName("주민번호가 없으면 마스킹도 없다 — 빈 자물쇠를 보여 주지 않는다")
    void maskOfMissingIsNull() {
        assertThat(ElderCareProfileSupport.mask(null)).isNull();
        assertThat(ElderCareProfileSupport.mask("")).isNull();
        assertThat(ElderCareProfileSupport.mask("41020328")).isNull();
    }

    @Test
    @DisplayName("뒷자리 첫 숫자가 세기를 정한다")
    void derivesBirthDateByCentury() {
        assertThat(ElderCareProfileSupport.deriveBirthDate("4102032830514")).isEqualTo(LocalDate.of(1941, 2, 3));
        assertThat(ElderCareProfileSupport.deriveBirthDate("0503014830514")).isEqualTo(LocalDate.of(2005, 3, 1));
        assertThat(ElderCareProfileSupport.deriveBirthDate("9912319830514")).isEqualTo(LocalDate.of(1899, 12, 31));
        assertThat(ElderCareProfileSupport.deriveBirthDate("4102035830514")).isEqualTo(LocalDate.of(1941, 2, 3));
    }

    @Test
    @DisplayName("없는 날짜가 적혀 있어도 저장을 막지는 않는다 — 생년월일만 비운다")
    void impossibleDateYieldsNull() {
        assertThat(ElderCareProfileSupport.deriveBirthDate("4113992830514")).isNull();
        assertThat(ElderCareProfileSupport.deriveBirthDate(null)).isNull();
        assertThat(ElderCareProfileSupport.deriveBirthDate("410203")).isNull();
    }

    @Test
    @DisplayName("성별은 뒷자리 첫 숫자의 홀짝이다")
    void derivesGender() {
        assertThat(ElderCareProfileSupport.deriveGender("4102031830514")).isEqualTo(Gender.MALE);
        assertThat(ElderCareProfileSupport.deriveGender("4102032830514")).isEqualTo(Gender.FEMALE);
        assertThat(ElderCareProfileSupport.deriveGender("0503013830514")).isEqualTo(Gender.MALE);
        assertThat(ElderCareProfileSupport.deriveGender("0503014830514")).isEqualTo(Gender.FEMALE);
        assertThat(ElderCareProfileSupport.deriveGender("9912319830514")).isEqualTo(Gender.MALE);
        assertThat(ElderCareProfileSupport.deriveGender(null)).isNull();
    }

    @Test
    @DisplayName("만 나이는 생일이 지나야 오른다")
    void ageCountsCompletedYears() {
        LocalDate birth = LocalDate.of(1941, 2, 3);

        assertThat(ElderCareProfileSupport.ageOf(birth, LocalDate.of(2026, 2, 2))).isEqualTo(84);
        assertThat(ElderCareProfileSupport.ageOf(birth, LocalDate.of(2026, 2, 3))).isEqualTo(85);
    }

    @Test
    @DisplayName("생년월일이 없거나 미래면 나이도 없다")
    void ageOfMissingIsNull() {
        assertThat(ElderCareProfileSupport.ageOf(null, LocalDate.of(2026, 9, 11))).isNull();
        assertThat(ElderCareProfileSupport.ageOf(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 9, 11))).isNull();
    }
}
