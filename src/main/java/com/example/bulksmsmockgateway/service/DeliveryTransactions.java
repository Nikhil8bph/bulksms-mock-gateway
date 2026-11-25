package com.example.bulksmsmockgateway.service;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.facade.AccountFacade;
import com.example.bulksmsmockgateway.facade.BlockedNumberFacade;
import com.example.bulksmsmockgateway.facade.MessageFacade;
import com.example.bulksmsmockgateway.facade.WebhookFacade;
import com.example.bulksmsmockgateway.validation.ApiInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
class DeliveryTransactions {
    private final AccountFacade accounts;
    private final MessageFacade messages;
    private final BlockedNumberFacade blocked;
    private final WebhookFacade webhooks;

    DeliveryTransactions(AccountFacade accounts, MessageFacade messages, BlockedNumberFacade blocked,
                         WebhookFacade webhooks) {
        this.accounts = accounts;
        this.messages = messages;
        this.blocked = blocked;
        this.webhooks = webhooks;
    }

    @Transactional
    public Optional<GatewayData.MailJob> claimMail(Instant now) {
        accounts.lockSandbox();
        for (int checked = 0; checked < 25; checked++) {
            var candidate = messages.claimMail(now);
            if (candidate.isEmpty()) {
                return Optional.empty();
            }
            var job = candidate.orElseThrow();
            if (!blocked.contains(ApiInput.phone(job.message().destination()))) {
                return candidate;
            }
            messages.finishMail(job, null, "BLOCKED", null)
                    .ifPresent(message -> publishDeliveryReport(message, now));
        }
        return Optional.empty();
    }

    @Transactional
    public void finishMail(GatewayData.MailJob job, String error, Instant now) {
        accounts.lockSandbox();
        Instant retry = error != null && job.attempts() < 5 ? now.plusSeconds(15L << (job.attempts() - 1)) : null;
        messages.finishMail(job, error, error == null ? null : "NOT_SENT", retry)
                .ifPresent(message -> publishDeliveryReport(message, now));
    }

    private void publishDeliveryReport(GatewayData.Message message, Instant now) {
        if ("ALL".equals(message.deliveryReports())
                || ("FAILED".equals(message.statusType()) && "ERRORS".equals(message.deliveryReports()))) {
            webhooks.enqueue(message, now);
        }
    }

    @Transactional
    public Optional<GatewayData.WebhookJob> claimWebhook(Instant now) {
        accounts.lockSandbox();
        return webhooks.claim(now);
    }

    @Transactional
    public void finishWebhook(GatewayData.WebhookJob job, int status, int retryAfter, Instant now) {
        accounts.lockSandbox();
        boolean success = status > 0 && status < 300;
        boolean permanentFailure = status >= 400 && status < 500 && status != 429;
        Instant retryAt = null;
        if (!success && !permanentFailure) {
            retryAt = now.plusSeconds(Math.max(retryAfter, Math.min(900, 90L << Math.min(4, job.attempt() - 1))));
        } else if (permanentFailure && job.ids().size() > 1) {
            // The next claim takes a single row because each member now has an attempt recorded.
            retryAt = now;
        }
        webhooks.finish(job, status, retryAt, now.minus(Duration.ofDays(2)));
    }
}
