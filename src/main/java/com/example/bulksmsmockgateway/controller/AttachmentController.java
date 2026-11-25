package com.example.bulksmsmockgateway.controller;

import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
public class AttachmentController {
    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping(value = "/v1/rmm/pre-sign-attachment", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "attachment.presign")
    Responses.PreSign presign(@RequestBody Requests.PreSign request) {
        return attachments.presign(request);
    }

    @PutMapping("/sandbox/attachments/{id}/upload")
    void upload(@PathVariable String id, @RequestParam String token, HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > AttachmentService.MAX_BYTES) {
            throw ApiException.tooLarge();
        }
        attachments.upload(id, token, request.getInputStream().readNBytes(AttachmentService.MAX_BYTES + 1));
    }

    @GetMapping("/sandbox/attachments/{id}")
    ResponseEntity<byte[]> fetch(@PathVariable String id) {
        var attachment = attachments.download(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(attachment.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("attachment." + attachment.extension()).build().toString())
                .body(attachment.contents());
    }
}
