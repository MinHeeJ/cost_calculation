package com.sicost.partnersops;

import com.sicost.common.ApiError;
import com.sicost.common.Meta;
import com.sicost.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps the common error wire intact while carrying structured PARTNER_IN_USE exclusions. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PartnerActivityController.class)
public class PartnerActivityExceptionHandler {
    @ExceptionHandler(PartnerActivityException.class)
    public ResponseEntity<?> business(PartnerActivityException error, HttpServletRequest request) {
        String id = RequestIdFilter.requestId(request);
        if (error.status() == 409) {
            return ResponseEntity.status(409).body(new InUseError(false, "PARTNER_IN_USE",
                error.getMessage(), error.field(), id, new Meta(id), error.excluded()));
        }
        return ResponseEntity.status(error.status()).body(ApiError.of(
            error.status(), error.getMessage(), error.field(), id));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "입력 형식을 확인해 주세요.", "ids",
            RequestIdFilter.requestId(request)));
    }

    public record InUseError(boolean success, String code, String message, String field,
        String requestId, Meta meta, List<PartnerDisableResult.Exclusion> excluded) { }
}
