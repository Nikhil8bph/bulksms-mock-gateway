package com.example.bulksmsmockgateway.dtos.request;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

public final class Requests {
    private Requests() {}

    public record Submission(JsonNode from, JsonNode to, String body, String routingGroup, String encoding,
                             Integer longMessageMaxParts, String userSuppliedId, JsonNode protocolId,
                             JsonNode messageClass, String deliveryReports) {}
    public record SendOptions(boolean autoUnicode, String scheduleDate, String scheduleDescription, Integer deduplicationId) {}
    public record Webhook(String name, String url, String contactEmailAddress, String triggerScope,
                          String invokeOption, Boolean active, Boolean onWebApp) {}
    public record Transfer(String toUsername, BigDecimal toUserId, BigDecimal credits, String commentOnFrom,
                           String commentOnTo) {}
    public record PreSign(String fileExtension, String mediaType) {}
    public record Inbound(String from, String to, String body) {}
}
