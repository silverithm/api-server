package com.silverithm.vehicleplacementsystem.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 비밀번호 길이 규칙: 가입·비밀번호 변경은 8자 이상. (로그인은 길이를 검증하지 않는다)
 */
class PasswordLengthValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    private MemberJoinRequestDTO joinRequestWithPassword(String password) {
        MemberJoinRequestDTO dto = new MemberJoinRequestDTO();
        dto.setUsername("tester1");
        dto.setPassword(password);
        dto.setName("테스터");
        dto.setEmail("tester@example.com");
        dto.setRole("STAFF");
        return dto;
    }

    private Set<String> passwordViolations(Object target) {
        return validator.validate(target).stream()
                .filter(v -> v.getPropertyPath().toString().contains("assword"))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("회원가입: 7자 비밀번호는 거부된다")
    void join_sevenCharPassword_rejected() {
        assertThat(passwordViolations(joinRequestWithPassword("1234567")))
                .contains("비밀번호는 8자 이상이어야 합니다");
    }

    @Test
    @DisplayName("회원가입: 8자 비밀번호는 통과한다")
    void join_eightCharPassword_accepted() {
        assertThat(passwordViolations(joinRequestWithPassword("12345678"))).isEmpty();
    }

    @Test
    @DisplayName("비밀번호 변경: 7자 새 비밀번호는 거부된다")
    void changePassword_sevenCharPassword_rejected() {
        PasswordChangeRequest request =
                new PasswordChangeRequest("tester@example.com", "oldPassword", "1234567");
        assertThat(passwordViolations(request))
                .contains("비밀번호는 8자 이상이어야 합니다");
    }

    @Test
    @DisplayName("비밀번호 변경: 8자 새 비밀번호는 통과한다")
    void changePassword_eightCharPassword_accepted() {
        PasswordChangeRequest request =
                new PasswordChangeRequest("tester@example.com", "oldPassword", "12345678");
        assertThat(passwordViolations(request)).isEmpty();
    }

    @Test
    @DisplayName("관리자 가입: 7자는 거부, 8자는 통과")
    void adminSignup_lengthRule() {
        assertThat(passwordViolations(userDataWithPassword("1234567")))
                .contains("비밀번호는 8자 이상이어야 합니다");
        assertThat(passwordViolations(userDataWithPassword("12345678"))).isEmpty();
    }

    private UserDataDTO userDataWithPassword(String password) {
        UserDataDTO dto = new UserDataDTO();
        setField(dto, "password", password);
        return dto;
    }

    private void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
