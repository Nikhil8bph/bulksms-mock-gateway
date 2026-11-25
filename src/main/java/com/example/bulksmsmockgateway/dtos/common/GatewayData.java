package com.example.bulksmsmockgateway.dtos.common;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class GatewayData {
    private GatewayData() {}

    public record Message(Long id, Long submissionId, Instant submittedAt, Instant availableAt,
                          String direction, String origin, String destination, String body, String encoding,
                          int protocolId, int messageClass, int numberOfParts, BigDecimal creditCost,
                          String statusType, String statusSubtype, Long relatedSentMessageId, String userSuppliedId,
                          boolean repliable, String routingGroup, String deliveryReports, String initialStatusType,
                          String initialStatusSubtype) {}
    public record MessageQuery(String filter, boolean ascending, int limit, Instant now) {}
    public record Submission(Long id, Instant submittedAt, Instant availableAt, String description) {}
    public record MailJob(Message message, String lease, int attempts) {}
    public record Webhook(Long id, String name, String url, String contactEmailAddress, String triggerScope,
                          String invokeOption, boolean active, boolean onWebApp) {}
    public record WebhookJob(Webhook webhook, List<Long> ids, List<Message> messages, String lease, int attempt) {}
    public record BlockedNumber(Long id, String phoneNumber, Instant created, String description) {}
    public record Account(Long id, String username, Instant created, BigDecimal balance, int creditLimit,
                          int dailyQuota, boolean transferAllowed) {}
    public record Transfer(String toUsername, Long toUserId, BigDecimal credits, String commentOnFrom,
                           String commentOnTo, Instant created) {}
    public record Attachment(String id, String uploadToken, String extension, String mediaType,
                             Instant created, Instant expires, byte[] contents) {}
    public record Contact(String phoneNumber, String firstName, String surname) {}
}
