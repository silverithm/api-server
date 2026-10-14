package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 실험실 컨트롤러의 오류 응답을 계약대로 {"message": ...}로 내린다.
 *
 * <p>전역 핸들러는 CustomException을 {"error": ...}로만 내려 주는데, 실험실 웹 클라이언트는 message를
 * 읽는다. 전역 핸들러를 바꾸면 다른 화면이 영향받으므로 실험실 컨트롤러에만 걸리는 별도 어드바이스를 둔다.
 * (호환을 위해 error 키도 같이 담는다)
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(basePackageClasses = LabExceptionHandler.class)
@ResponseBody
public class LabExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, String>> handleCustom(CustomException ex) {
        return body(ex.getHttpStatus(), ex.getMessage());
    }

    /** 날짜·JSON 형식이 틀린 본문 — 500이 아니라 400으로 알려야 화면이 고칠 곳을 보여 줄 수 있다 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("[Lab] 읽을 수 없는 요청 본문: {}", ex.getMostSpecificCause().getMessage());
        return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다. 날짜(YYYY-MM-DD)와 숫자를 확인해 주세요");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> handleBadParameter(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다");
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message, "error", message));
    }
}
