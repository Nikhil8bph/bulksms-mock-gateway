package com.example.bulksmsmockgateway.exception;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    private ApiException(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static ApiException invalid(String detail) {
        return new ApiException(400, "bad-request", detail);
    }

    public static ApiException missing(String detail) {
        return new ApiException(404, "not-found", detail);
    }

    public static ApiException credits() {
        return new ApiException(403, "insufficient-credits", "Insufficient sandbox credits");
    }

    public static ApiException quota() {
        return new ApiException(403, "insufficient-quota", "Daily sandbox messaging quota exceeded");
    }

    public static ApiException batchTooLarge() {
        return new ApiException(400, "max-batch-size-exceeded", "A submission can include up to 50,000 messages");
    }

    public static ApiException invalidGroup() {
        return new ApiException(400, "invalid-group", "The sandbox phonebook group has no members");
    }

    public static ApiException tooLarge() {
        return new ApiException(413, "payload-too-large", "Attachment exceeds 20 MB");
    }
}
