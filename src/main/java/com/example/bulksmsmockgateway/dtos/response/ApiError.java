package com.example.bulksmsmockgateway.dtos.response;

import org.springframework.http.HttpStatus;

public record ApiError(String type, String title, int status, String detail) {
    public static ApiError of(int status, String code, String detail) {
        String title = switch (code) {
            case "insufficient-credits" -> "Insufficient Credits";
            case "insufficient-quota" -> "Insufficient Quota";
            default -> HttpStatus.valueOf(status).getReasonPhrase();
        };
        return new ApiError("https://developer.bulksms.com/json/v1/errors/#" + code, title, status, detail);
    }
}
