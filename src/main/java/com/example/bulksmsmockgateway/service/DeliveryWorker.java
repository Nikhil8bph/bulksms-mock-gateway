package com.example.bulksmsmockgateway.service;

import com.example.bulksmsmockgateway.clients.GmailClient;
import com.example.bulksmsmockgateway.clients.WebhookClient;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.mapper.ApiMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Component
@ConditionalOnProperty(name = "sandbox.dispatch-enabled", havingValue = "true")
class DeliveryWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryWorker.class);
    private final DeliveryTransactions transactions;
    private final GmailClient gmail;
    private final WebhookClient webhooks;
    private final ApiMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final AttachmentService attachments;

    DeliveryWorker(DeliveryTransactions transactions, GmailClient gmail, WebhookClient webhooks,
                   ApiMapper mapper, ObjectMapper json, Clock clock, AttachmentService attachments) {
        this.transactions = transactions;
        this.gmail = gmail;
        this.webhooks = webhooks;
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
        this.attachments = attachments;
    }

    @Scheduled(fixedDelayString = "${sandbox.dispatch-delay:1000}")
    void dispatch() {
        for (int i = 0; i < 25; i++) {
            boolean mail = dispatchMail();
            boolean callback = dispatchWebhook();
            if (!mail && !callback) {
                return;
            }
        }
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 3600000)
    void purgeAttachments() {
        attachments.purgeExpired();
    }

    private boolean dispatchMail() {
        var candidate = transactions.claimMail(clock.instant());
        if (candidate.isEmpty()) {
            return false;
        }
        var job = candidate.orElseThrow();
        String error = null;
        try {
            gmail.send(job.message());
        } catch (MailException ex) {
            error = ex.getClass().getSimpleName();
            LOGGER.warn("SMTP failed for message {} on attempt {} ({})", job.message().id(), job.attempts(), error);
        }
        transactions.finishMail(job, error, clock.instant());
        return true;
    }

    private boolean dispatchWebhook() {
        var candidate = transactions.claimWebhook(clock.instant());
        if (candidate.isEmpty()) {
            return false;
        }
        var job = candidate.orElseThrow();
        WebhookClient.Result result;
        try {
            var payload = job.messages().stream().map(mapper::message).toList();
            result = webhooks.deliver(job, json.writeValueAsString(payload));
        } catch (ApiException ex) {
            result = new WebhookClient.Result(403, 0);
        }
        transactions.finishWebhook(job, result.status(), result.retryAfter(), clock.instant());
        return true;
    }
}
