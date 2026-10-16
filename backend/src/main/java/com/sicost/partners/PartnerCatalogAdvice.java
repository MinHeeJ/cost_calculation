package com.sicost.partners;

import com.sicost.common.ApiError;
import com.sicost.common.Meta;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Only catalog endpoints use this error translation; other API envelopes remain untouched. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PartnerCatalogController.class)
public class PartnerCatalogAdvice {
    @ExceptionHandler(PartnerCatalogException.class)
    public ResponseEntity<ApiError> business(PartnerCatalogException error, HttpServletRequest request) {
        String id = RequestIdFilter.requestId(request);
        return ResponseEntity.status(error.status()).body(new ApiError(
            false, error.code(), error.getMessage(), error.field(), id, new Meta(id)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> type(MethodArgumentTypeMismatchException error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "입력 형식을 확인해 주세요.",
            error.getName(), RequestIdFilter.requestId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "입력 형식을 확인해 주세요.",
            null, RequestIdFilter.requestId(request)));
    }
}
