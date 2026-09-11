package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile.Gender;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import org.springframework.http.HttpStatus;

/**
 * 주민번호를 다루는 순수 규칙 — 정규화·마스킹·생년월일/성별 파생·만 나이.
 *
 * <p>서비스에서 떼어 둔 이유는 하나다. 이 규칙들이 틀리면 화면에 남의 생년월일이 찍히는데,
 * DB도 스프링도 없이 바로 검증할 수 있어야 그 실수를 테스트로 못박을 수 있다.
 */
public final class ElderCareProfileSupport {

    private ElderCareProfileSupport() {
    }

    /**
     * 하이픈·공백을 걷어내고 13자리 숫자인지 확인한다.
     *
     * @return 숫자 13자리. 입력이 null이거나 빈 값이면 그대로 돌려준다(유지/삭제 판단은 호출자 몫)
     * @throws CustomException 13자리 숫자가 아닌 경우 400
     */
    public static String normalizeResidentNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[\\s-]", "");
        if (digits.isEmpty()) {
            return "";
        }
        if (!digits.matches("\\d{13}")) {
            throw new CustomException("주민등록번호는 숫자 13자리여야 합니다", HttpStatus.BAD_REQUEST);
        }
        return digits;
    }

    /** {@code "410203-2******"} — 뒷자리는 성별 한 자리만 남긴다 */
    public static String mask(String residentNumber) {
        if (residentNumber == null || residentNumber.isBlank()) {
            return null;
        }
        String digits = residentNumber.replaceAll("[\\s-]", "");
        if (digits.length() != 13) {
            return null;
        }
        return digits.substring(0, 6) + "-" + digits.charAt(6) + "******";
    }

    /**
     * 주민번호에서 생년월일을 뽑는다.
     *
     * <p>세기는 뒷자리 첫 숫자가 정한다. 1·2·5·6=19xx, 3·4·7·8=20xx, 9·0=18xx.
     * 요양 현장에는 1900년대생이 대부분이지만 1800년대생 표기가 남은 이력도 있어 함께 본다.
     *
     * @return 파싱할 수 없으면 null (없는 날짜가 적힌 옛 자료를 이유로 저장을 막지는 않는다)
     */
    public static LocalDate deriveBirthDate(String residentNumber) {
        String digits = digitsOrNull(residentNumber);
        if (digits == null) {
            return null;
        }
        int century = switch (digits.charAt(6)) {
            case '1', '2', '5', '6' -> 1900;
            case '3', '4', '7', '8' -> 2000;
            case '9', '0' -> 1800;
            default -> -1;
        };
        if (century < 0) {
            return null;
        }
        try {
            int year = century + Integer.parseInt(digits.substring(0, 2));
            int month = Integer.parseInt(digits.substring(2, 4));
            int day = Integer.parseInt(digits.substring(4, 6));
            return LocalDate.of(year, month, day);
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    /** 뒷자리 첫 숫자의 홀짝이 성별이다(1·3·5·7·9=남, 2·4·6·8·0=여) */
    public static Gender deriveGender(String residentNumber) {
        String digits = digitsOrNull(residentNumber);
        if (digits == null) {
            return null;
        }
        char flag = digits.charAt(6);
        if (flag < '0' || flag > '9') {
            return null;
        }
        return (flag - '0') % 2 == 1 ? Gender.MALE : Gender.FEMALE;
    }

    /** 오늘 기준 만 나이. 생년월일이 없거나 미래면 null */
    public static Integer ageOf(LocalDate birthDate) {
        return ageOf(birthDate, LocalDate.now());
    }

    /** 기준일을 받는 버전 — 테스트가 '오늘'에 흔들리지 않게 한다 */
    public static Integer ageOf(LocalDate birthDate, LocalDate today) {
        if (birthDate == null || birthDate.isAfter(today)) {
            return null;
        }
        return Period.between(birthDate, today).getYears();
    }

    private static String digitsOrNull(String residentNumber) {
        if (residentNumber == null || residentNumber.isBlank()) {
            return null;
        }
        String digits = residentNumber.replaceAll("[\\s-]", "");
        return digits.length() == 13 ? digits : null;
    }
}
