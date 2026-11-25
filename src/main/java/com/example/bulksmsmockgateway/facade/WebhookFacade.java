package com.example.bulksmsmockgateway.facade;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.entities.WebhookDeliveryEntity;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.mapper.PersistenceMapper;
import com.example.bulksmsmockgateway.repo.MessageRepository;
import com.example.bulksmsmockgateway.repo.WebhookDeliveryRepository;
import com.example.bulksmsmockgateway.repo.WebhookRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class WebhookFacade {
    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final MessageRepository messages;
    private final PersistenceMapper mapper;

    public WebhookFacade(WebhookRepository webhooks, WebhookDeliveryRepository deliveries, MessageRepository messages,
                         PersistenceMapper mapper) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.messages = messages;
        this.mapper = mapper;
    }

    public List<GatewayData.Webhook> list() {
        return webhooks.findAll(Sort.by("id")).stream().map(mapper::webhook).toList();
    }

    public Optional<GatewayData.Webhook> find(long id) {
        return webhooks.findById(id).map(mapper::webhook);
    }

    public GatewayData.Webhook save(GatewayData.Webhook data) {
        webhooks.findByName(data.name()).filter(found -> !Objects.equals(found.id, data.id())).ifPresent(found -> {
            throw ApiException.invalid("A webhook with that name already exists");
        });
        if (data.id() != null) {
            var current = webhooks.findById(data.id())
                    .orElseThrow(() -> ApiException.missing("Webhook with id '" + data.id() + "' does not exist"));
            if (!current.triggerScope.equals(data.triggerScope())) {
                deliveries.cancelPending(data.id());
            }
        }
        return mapper.webhook(webhooks.save(mapper.webhook(data)));
    }

    public void requireExists(long id) {
        if (!webhooks.existsById(id)) {
            throw ApiException.missing("Webhook with id '" + id + "' does not exist");
        }
    }

    public void delete(long id) {
        webhooks.deleteById(id);
    }

    public void enqueue(GatewayData.Message message, Instant now) {
        for (var webhook : webhooks.findByActiveTrueAndTriggerScope(message.direction())) {
            var delivery = new WebhookDeliveryEntity();
            delivery.webhookId = webhook.id;
            delivery.messageId = message.id();
            delivery.created = now;
            delivery.due = message.availableAt().isAfter(now) ? message.availableAt() : now;
            deliveries.save(delivery);
        }
    }

    public Optional<GatewayData.WebhookJob> claim(Instant now) {
        for (int skipped = 0; skipped < 100; skipped++) {
            var candidate = deliveries.findFirstByDueLessThanEqualOrderByDueAscIdAsc(now);
            if (candidate.isEmpty()) {
                return Optional.empty();
            }
            var first = candidate.orElseThrow();
            var webhook = webhooks.findById(first.webhookId).orElse(null);
            if (webhook == null || !webhook.active) {
                first.due = null;
                first.lease = null;
                deliveries.flush();
                continue;
            }
            var batch = webhook.invokeOption.equals("MANY") && first.attempt == 0
                    ? deliveries.findByWebhookIdAndAttemptAndDueLessThanEqualOrderByDueAscIdAsc(
                            first.webhookId, 0, now, PageRequest.ofSize(10))
                    : List.of(first);
            String lease = UUID.randomUUID().toString();
            for (var delivery : batch) {
                delivery.lease = lease;
                delivery.due = now.plus(Duration.ofMinutes(5));
                delivery.attempt++;
            }
            var payload = messages.findAllById(batch.stream().map(delivery -> delivery.messageId).toList())
                    .stream().sorted(Comparator.comparing(message -> message.id)).map(mapper::message).toList();
            return Optional.of(new GatewayData.WebhookJob(mapper.webhook(webhook),
                    batch.stream().map(delivery -> delivery.id).toList(), payload, lease, first.attempt));
        }
        return Optional.empty();
    }

    public void finish(GatewayData.WebhookJob job, int status, Instant retryAt, Instant oldestRetry) {
        var rows = deliveries.findAllById(job.ids()).stream()
                .filter(delivery -> Objects.equals(delivery.lease, job.lease())).toList();
        if (rows.isEmpty()) {
            return;
        }
        for (var delivery : rows) {
            delivery.lastStatus = status;
            delivery.lease = null;
            delivery.due = job.ids().size() == 1 && delivery.created.isBefore(oldestRetry) ? null : retryAt;
        }
        if (status == 410 && job.ids().size() == 1) {
            webhooks.findById(job.webhook().id())
                    .filter(hook -> hook.url.equals(job.webhook().url()) && hook.triggerScope.equals(job.webhook().triggerScope()))
                    .ifPresent(hook -> hook.active = false);
        }
    }
}
