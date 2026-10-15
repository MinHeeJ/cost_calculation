package com.sicost.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 오류는 직접 ApiError로 응답하고 내부 SQL·접속값·stacktrace를 노출하지 않는다. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> validation(ValidationException error, HttpServletRequest request) {
        return error(400, error.getMessage(), error.field(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missing(MissingServletRequestParameterException error, HttpServletRequest request) {
        return error(400, "필수 항목입니다.", error.getParameterName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> type(MethodArgumentTypeMismatchException error, HttpServletRequest request) {
        return error(400, "입력 형식을 확인해 주세요.", error.getName(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> beanValidation(MethodArgumentNotValidException error, HttpServletRequest request) {
        var field = error.getBindingResult().getFieldError();
        return error(400, "입력값을 확인해 주세요.", field == null ? null : field.getField(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> notFound(Exception ignored, HttpServletRequest request) {
        return error(404, "요청한 경로를 찾을 수 없습니다.", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ignored, HttpServletRequest request) {
        return error(500, "처리 중 오류가 발생했습니다. 다시 시도해 주세요.", null, request);
    }

    private ResponseEntity<ApiError> error(int status, String message, String field, HttpServletRequest request) {
        return ResponseEntity.status(status)
            .body(ApiError.of(status, message, field, RequestIdFilter.requestId(request)));
    }
}
