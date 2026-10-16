package com.sicost.codegroups;

import com.sicost.common.ApiError;
import com.sicost.common.Meta;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 공유 예외 처리기를 바꾸지 않고 공통코드의 명시 업무 오류만 확장한다. */
@Order(-10)
@RestControllerAdvice(assignableTypes = CodeGroupController.class)
public class CodeGroupAdvice {
    @ExceptionHandler(CodeGroupException.class)
    public ResponseEntity<ApiError> business(CodeGroupException error, HttpServletRequest request) {
        String id = RequestIdFilter.requestId(request);
        return ResponseEntity.status(error.status()).body(
            new ApiError(false, error.code(), error.getMessage(), null, id, new Meta(id)));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateKeyException ignored, HttpServletRequest request) {
        return business(new CodeGroupException(409, "DUPLICATE_CODE", "이미 있는 코드입니다."), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpMessageNotReadableException ignored, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
            ApiError.of(400, "입력 형식을 확인해 주세요.", null, RequestIdFilter.requestId(request)));
    }
}
