package com.example.bulksmsmockgateway.controller;

import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class InboundController {
    private final MessageService messages;

    public InboundController(MessageService messages) {
        this.messages = messages;
    }

    @PostMapping(value = "/sandbox/inbound-messages", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Responses.Message receive(@RequestBody Requests.Inbound request) {
        return messages.receive(request);
    }
}
