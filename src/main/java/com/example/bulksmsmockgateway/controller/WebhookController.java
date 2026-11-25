package com.example.bulksmsmockgateway.controller;

import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Webhooks")
@RequestMapping(value = "/v1/webhooks", produces = MediaType.APPLICATION_JSON_VALUE)
public class WebhookController {
    private final WebhookService webhooks;

    public WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @GetMapping
    @Operation(operationId = "webhook.list")
    List<Responses.Webhook> list() {
        return webhooks.list();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "webhook.show")
    Responses.Webhook find(@PathVariable String id) {
        return webhooks.find(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "webhook.create")
    Responses.Webhook create(@RequestBody Requests.Webhook request) {
        return webhooks.save(null, request);
    }

    @PostMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "webhook.update")
    Responses.Webhook update(@PathVariable String id, @RequestBody Requests.Webhook request) {
        return webhooks.save(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "webhook.delete")
    void delete(@PathVariable String id) {
        webhooks.delete(id);
    }
}
