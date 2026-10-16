package com.sicost.operatingstandards;

import com.sicost.common.ApiError;
import com.sicost.common.Meta;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 기능 내부 업무 오류와 잘못된 JSON만 처리하며 공통 안전한 500 처리는 보존한다. */
@Order(-1)
@RestControllerAdvice(assignableTypes = OperatingStandardController.class)
public class OperatingStandardAdvice {
    @ExceptionHandler(OperatingStandardException.class)
    public ResponseEntity<ApiError> business(OperatingStandardException error, HttpServletRequest request) {
        String id = RequestIdFilter.requestId(request);
        return ResponseEntity.status(error.status).body(
            new ApiError(false, error.code, error.getMessage(), error.field, id, new Meta(id))
        );
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
        com.fasterxml.jackson.core.JsonProcessingException.class})
    public ResponseEntity<ApiError> malformed(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
            ApiError.of(400, "입력 형식을 확인해 주세요.", null, RequestIdFilter.requestId(request))
        );
    }
}
