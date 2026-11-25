package com.example.bulksmsmockgateway.service;

import com.example.bulksmsmockgateway.clients.WebhookClient;
import com.example.bulksmsmockgateway.config.SandboxSettings;
import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.facade.AccountFacade;
import com.example.bulksmsmockgateway.facade.WebhookFacade;
import com.example.bulksmsmockgateway.mapper.ApiMapper;
import com.example.bulksmsmockgateway.validation.ApiInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class WebhookService {
    private final WebhookFacade webhooks;
    private final AccountFacade accounts;
    private final ApiMapper mapper;
    private final WebhookClient client;

    public WebhookService(WebhookFacade webhooks, AccountFacade accounts, ApiMapper mapper, WebhookClient client) {
        this.webhooks = webhooks;
        this.accounts = accounts;
        this.mapper = mapper;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public List<Responses.Webhook> list() {
        return webhooks.list().stream().map(mapper::webhook).toList();
    }

    @Transactional(readOnly = true)
    public Responses.Webhook find(String id) {
        return mapper.webhook(load(id));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Responses.Webhook save(String id, Requests.Webhook request) {
        ApiInput.required(request.name(), "name", 100);
        ApiInput.required(request.url(), "url", 2048);
        ApiInput.required(request.triggerScope(), "triggerScope", 8);
        ApiInput.choice(request.triggerScope(), null, "triggerScope", Set.of("SENT", "RECEIVED"));
        ApiInput.choice(request.invokeOption(), "ONE", "invokeOption", Set.of("ONE", "MANY"));
        if (request.contactEmailAddress() != null) {
            try {
                SandboxSettings.validateEmail(request.contactEmailAddress(), "contactEmailAddress");
                ApiInput.bounded(request.contactEmailAddress(), "contactEmailAddress", 254);
            } catch (IllegalArgumentException ex) {
                throw ApiException.invalid(ex.getMessage());
            }
        }
        GatewayData.Webhook data = mapper.webhook(request);
        client.allowed(data.url());
        if (id != null) {
            long webhookId = ApiInput.id(id);
            webhooks.requireExists(webhookId);
            data = new GatewayData.Webhook(webhookId, data.name(), data.url(), data.contactEmailAddress(),
                    data.triggerScope(), data.invokeOption(), data.active(), data.onWebApp());
        }
        if (data.active()) {
            int status = client.validate(data).status();
            if (status < 200 || status >= 300) {
                throw ApiException.invalid("Invoking the webhook URL caused an error (" + (status == 0 ? "unreachable" : status) + ")");
            }
        }
        accounts.lockSandbox();
        return mapper.webhook(webhooks.save(data));
    }

    @Transactional
    public void delete(String id) {
        accounts.lockSandbox();
        webhooks.delete(load(id).id());
    }

    private GatewayData.Webhook load(String id) {
        return webhooks.find(ApiInput.id(id))
                .orElseThrow(() -> ApiException.missing("Webhook with id '" + id + "' does not exist"));
    }
}
