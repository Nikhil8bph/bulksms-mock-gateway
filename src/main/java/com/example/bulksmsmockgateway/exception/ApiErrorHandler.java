package com.example.bulksmsmockgateway.exception;

import com.example.bulksmsmockgateway.dtos.response.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class ApiErrorHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> apiError(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(ApiError.of(ex.status(), ex.code(), ex.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                            HttpStatusCode status, WebRequest request) {
        String code = switch (status.value()) {
            case 404 -> "not-found";
            case 405 -> "method-not-allowed";
            case 415 -> "unsupported-media-type";
            default -> status.is4xxClientError() ? "bad-request" : "internal-server-error";
        };
        String detail = ex instanceof ErrorResponse error ? error.getBody().getDetail() : "Invalid request";
        return new ResponseEntity<>(ApiError.of(status.value(), code, detail), headers, status);
    }
}
