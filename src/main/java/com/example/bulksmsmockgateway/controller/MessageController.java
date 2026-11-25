package com.example.bulksmsmockgateway.controller;

import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

import java.util.ArrayList;
import java.util.List;

@RestController
@Tag(name = "Message")
@RequestMapping(value = "/v1/messages", produces = MediaType.APPLICATION_JSON_VALUE)
public class MessageController {
    private final MessageService messages;
    private final ObjectMapper json;

    public MessageController(MessageService messages, ObjectMapper json) {
        this.messages = messages;
        this.json = json;
    }

    @GetMapping
    @Operation(operationId = "message.list")
    List<Responses.Message> list(@RequestParam(required = false) String filter,
                                 @RequestParam(defaultValue = "DESCENDING") String sortOrder,
                                 @RequestParam(defaultValue = "1000") int limit) {
        return messages.list(filter, sortOrder, limit);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "message.show")
    Responses.Message find(@PathVariable String id) {
        return messages.find(id);
    }

    @GetMapping("/{id}/relatedReceivedMessages")
    @Operation(operationId = "message.related.received")
    List<Responses.Message> related(@PathVariable String id) {
        return messages.related(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "message.send")
    List<Responses.Message> send(@RequestBody JsonNode body,
                                 @RequestParam(name = "auto-unicode", defaultValue = "false") boolean autoUnicode,
                                 @RequestParam(name = "schedule-date", required = false) String scheduleDate,
                                 @RequestParam(name = "schedule-description", required = false) String scheduleDescription,
                                 @RequestParam(name = "deduplication-id", required = false) Integer deduplicationId) {
        List<Requests.Submission> entries = new ArrayList<>();
        if (body.isArray()) {
            for (JsonNode entry : body) {
                entries.add(parse(entry));
            }
        } else {
            entries.add(parse(body));
        }
        return messages.send(entries, new Requests.SendOptions(autoUnicode, scheduleDate, scheduleDescription, deduplicationId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    List<Responses.Message> sendForm(@RequestParam String to, @RequestParam String body,
                                     @RequestParam(name = "auto-unicode", defaultValue = "false") boolean autoUnicode,
                                     @RequestParam(name = "schedule-date", required = false) String scheduleDate,
                                     @RequestParam(name = "schedule-description", required = false) String scheduleDescription,
                                     @RequestParam(name = "deduplication-id", required = false) Integer deduplicationId) {
        return messages.send(List.of(simple(to, body)),
                new Requests.SendOptions(autoUnicode, scheduleDate, scheduleDescription, deduplicationId));
    }

    @RequestMapping(value = "/send", method = RequestMethod.HEAD)
    ResponseEntity<Void> rejectHeadSend() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(HttpMethod.GET).build();
    }

    @GetMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "message.send.simple.get")
    List<Responses.Message> sendSimple(@RequestParam String to, @RequestParam String body,
                                       @RequestParam(name = "deduplication-id", required = false) Integer deduplicationId) {
        return messages.send(List.of(simple(to, body)), new Requests.SendOptions(false, null, null, deduplicationId));
    }

    private Requests.Submission parse(JsonNode node) {
        if (!node.isObject()) {
            throw ApiException.invalid("Each submission must be an object");
        }
        if (!node.path("body").isTextual()) {
            throw ApiException.invalid("body must be a string");
        }
        try {
            return json.treeToValue(node, Requests.Submission.class);
        } catch (tools.jackson.core.JacksonException ex) {
            throw ApiException.invalid("Invalid message submission");
        }
    }

    private static Requests.Submission simple(String to, String body) {
        return new Requests.Submission(null, StringNode.valueOf(to), body, null, null, null, null, null, null, null);
    }
}
