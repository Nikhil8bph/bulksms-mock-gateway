package com.example.bulksmsmockgateway.service;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.facade.AccountFacade;
import com.example.bulksmsmockgateway.facade.BlockedNumberFacade;
import com.example.bulksmsmockgateway.facade.MessageFacade;
import com.example.bulksmsmockgateway.facade.WebhookFacade;
import com.example.bulksmsmockgateway.mapper.ApiMapper;
import com.example.bulksmsmockgateway.validation.ApiInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class MessageService {
    private final MessageFacade messages;
    private final AccountFacade accounts;
    private final BlockedNumberFacade blocked;
    private final WebhookFacade webhooks;
    private final MessageComposer composer;
    private final ApiMapper mapper;
    private final Clock clock;

    public MessageService(MessageFacade messages, AccountFacade accounts, BlockedNumberFacade blocked,
                          WebhookFacade webhooks, MessageComposer composer, ApiMapper mapper, Clock clock) {
        this.messages = messages;
        this.accounts = accounts;
        this.blocked = blocked;
        this.webhooks = webhooks;
        this.composer = composer;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Responses.Message> list(String filter, String sortOrder, int limit) {
        if (limit <= 0) {
            throw ApiException.invalid("limit must be positive");
        }
        boolean ascending = switch (sortOrder) {
            case "ASCENDING" -> true;
            case "DESCENDING" -> false;
            default -> throw ApiException.invalid("sortOrder must be ASCENDING or DESCENDING");
        };
        var query = new GatewayData.MessageQuery(filter, ascending, limit, clock.instant());
        return messages.list(query).stream().map(mapper::message).toList();
    }

    @Transactional(readOnly = true)
    public Responses.Message find(String id) {
        return messages.find(ApiInput.id(id), clock.instant()).map(mapper::message)
                .orElseThrow(() -> ApiException.missing("Message with id '" + id + "' does not exist"));
    }

    @Transactional(readOnly = true)
    public List<Responses.Message> related(String id) {
        return messages.related(ApiInput.id(id)).stream().map(mapper::message).toList();
    }

    @Transactional
    public List<Responses.Message> send(List<Requests.Submission> entries, Requests.SendOptions options) {
        var account = accounts.lockSandbox();
        Instant now = clock.instant();
        Integer key = options.deduplicationId();
        if (key != null) {
            var previous = messages.duplicate(key, now.minus(Duration.ofHours(12)));
            if (previous.isPresent()) {
                return previous.orElseThrow().stream().map(mapper::submitted).toList();
            }
        }
        Instant available = availableAt(options.scheduleDate(), now);
        String description = options.scheduleDate() == null ? null : options.scheduleDescription();
        if (description != null && description.codePointCount(0, description.length()) > 256) {
            description = description.substring(0, description.offsetByCodePoints(0, 256));
        }
        var prepared = composer.compose(entries, options.autoUnicode());
        long sentToday = messages.sentSince(now.truncatedTo(ChronoUnit.DAYS));
        if (sentToday + prepared.size() > account.dailyQuota()) {
            throw ApiException.quota();
        }
        BigDecimal cost = BigDecimal.valueOf(prepared.stream().mapToLong(entry -> entry.content().parts()).sum());
        if (account.balance().compareTo(cost) < 0) {
            throw ApiException.credits();
        }
        var blockedPhones = blocked.matching(prepared.stream().map(MessageComposer.PreparedMessage::phone).toList());
        accounts.changeBalance(account.id(), cost.negate());
        Long submissionId = messages.createSubmission(new GatewayData.Submission(null, now, available, description), key).id();
        List<Responses.Message> response = new ArrayList<>(prepared.size());
        for (var entry : prepared) {
            boolean isBlocked = blockedPhones.contains(entry.phone());
            String status = isBlocked ? "FAILED" : "UNKNOWN";
            String initialStatus = isBlocked ? "FAILED" : "ACCEPTED";
            String subtype = isBlocked ? "BLOCKED" : null;
            boolean scheduled = available.isAfter(now);
            var message = new GatewayData.Message(null, submissionId, now, available, "SENT",
                    entry.sender().address(), "+" + entry.phone(), entry.content().body(), entry.content().encoding(),
                    entry.protocolId(), entry.messageClass(), entry.content().parts(), BigDecimal.valueOf(entry.content().parts()).setScale(4),
                    status, subtype, null, entry.userSuppliedId(), entry.sender().repliable(), entry.routing(),
                    entry.deliveryReports(), scheduled ? "SCHEDULED" : initialStatus, scheduled ? null : subtype);
            var stored = messages.create(message, isBlocked ? null : available);
            if (isBlocked && !"NONE".equals(entry.deliveryReports())) {
                webhooks.enqueue(stored, now);
            }
            response.add(mapper.submitted(stored));
        }
        return response;
    }

    @Transactional
    public Responses.Message receive(Requests.Inbound request) {
        accounts.lockSandbox();
        Instant now = clock.instant();
        String sender = ApiInput.phone(request.from());
        String to = "+" + ApiInput.phone(request.to());
        var body = MessageComposer.encode(ApiInput.required(request.body(), "body", 16_000), "TEXT", true);
        Long related = messages.replyTarget("+" + sender, to, now)
                .map(GatewayData.Message::id).orElse(null);
        Long submissionId = messages.createSubmission(new GatewayData.Submission(null, now, now, null), null).id();
        var received = new GatewayData.Message(null, submissionId, now, now, "RECEIVED", "+" + sender, to,
                body.body(), body.encoding(), 0, 2, body.parts(), BigDecimal.ZERO, "DELIVERED", null, related,
                null, false, "STANDARD", "ALL", "DELIVERED", null);
        var stored = messages.create(received, null);
        if (related != null && "STOP".equalsIgnoreCase(body.body().strip())) {
            blocked.insert(List.of(new GatewayData.BlockedNumber(null, sender, now, "STOP reply")));
        }
        webhooks.enqueue(stored, now);
        return mapper.message(stored);
    }

    private static Instant availableAt(String value, Instant now) {
        if (value == null) {
            return now;
        }
        try {
            Instant requested = OffsetDateTime.parse(value).toInstant();
            if (requested.isAfter(now.atOffset(ZoneOffset.UTC).plusYears(2).toInstant())) {
                throw ApiException.invalid("schedule-date cannot be more than two years in the future");
            }
            return requested.isAfter(now) ? requested : now;
        } catch (DateTimeParseException ex) {
            throw ApiException.invalid("schedule-date must include a UTC offset");
        }
    }
}
