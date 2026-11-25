package com.example.bulksmsmockgateway.service;

import com.example.bulksmsmockgateway.config.SandboxSettings;
import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.facade.AttachmentFacade;
import com.example.bulksmsmockgateway.mapper.ApiMapper;
import com.example.bulksmsmockgateway.validation.ApiInput;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private final AttachmentFacade attachments;
    private final SandboxSettings settings;
    private final ApiMapper mapper;
    private final Clock clock;

    public AttachmentService(AttachmentFacade attachments, SandboxSettings settings, ApiMapper mapper, Clock clock) {
        this.attachments = attachments;
        this.settings = settings;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public Responses.PreSign presign(Requests.PreSign request) {
        String extension = request.fileExtension() == null ? "bin" : request.fileExtension();
        if (!extension.matches("[A-Za-z0-9]{1,20}")) {
            throw ApiException.invalid("fileExtension must contain 1 to 20 letters or digits");
        }
        String mediaType = request.mediaType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : request.mediaType();
        ApiInput.bounded(mediaType, "mediaType", 120);
        try {
            if (!MediaType.parseMediaType(mediaType).isConcrete()) {
                throw ApiException.invalid("mediaType must be concrete");
            }
        } catch (IllegalArgumentException ex) {
            throw ApiException.invalid("Invalid mediaType");
        }
        var now = clock.instant();
        var data = attachments.create(new GatewayData.Attachment(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), extension, mediaType, now, now.plus(Duration.ofDays(30)), null));
        String base = settings.publicBaseUrl().toASCIIString().replaceFirst("/+$", "");
        String fetch = base + "/sandbox/attachments/" + data.id();
        return new Responses.PreSign(fetch + "/upload?token=" + data.uploadToken(), fetch,
                List.of(new Responses.Header("Content-Type", data.mediaType())));
    }

    @Transactional
    public void upload(String id, String token, byte[] contents) {
        if (contents.length > MAX_BYTES) {
            throw ApiException.tooLarge();
        }
        attachments.store(id, token, clock.instant(), contents);
    }

    @Transactional(readOnly = true)
    public Responses.Download download(String id) {
        return attachments.find(id, clock.instant()).map(mapper::download)
                .orElseThrow(() -> ApiException.missing("Attachment does not exist or has expired"));
    }

    @Transactional
    public void purgeExpired() {
        attachments.purgeExpired(clock.instant());
    }
}
