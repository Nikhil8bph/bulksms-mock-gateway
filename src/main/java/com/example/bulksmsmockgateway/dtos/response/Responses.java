package com.example.bulksmsmockgateway.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Responses {
    private Responses() {}

    public record Message(String id, String type, String from, String to, String body, String encoding,
                          int protocolId, int messageClass,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Integer numberOfParts,
                          @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal creditCost,
                          Submission submission, Status status, String relatedSentMessageId, String userSuppliedId) {}
    public record Submission(String id, Instant date) {}
    public record Status(String id, String type, String subtype) {}
    public record Webhook(Long id, String name, String url, String contactEmailAddress, String triggerScope,
                          String invokeOption, boolean active, boolean onWebApp) {}
    public record BlockedNumber(Long id, String phoneNumber, Instant created, String description) {}
    public record Profile(String id, String username, Instant created, Credits credits, Quota quota,
                          OriginAddresses originAddresses) {}
    public record Credits(BigDecimal balance, int limit, boolean isTransferAllowed) {}
    public record Quota(int size, int remaining) {}
    public record OriginAddresses(boolean isFullControlAllowed, List<String> allowed) {}
    public record PreSign(String putUrl, String fetchUrl, List<Header> fields) {}
    public record Header(String name, String value) {}
    public record Download(String mediaType, String extension, byte[] contents) {}
}
