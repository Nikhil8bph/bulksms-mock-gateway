package com.example.bulksmsmockgateway.facade;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.mapper.PersistenceMapper;
import com.example.bulksmsmockgateway.repo.AttachmentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class AttachmentFacade {
    private final AttachmentRepository attachments;
    private final PersistenceMapper mapper;

    public AttachmentFacade(AttachmentRepository attachments, PersistenceMapper mapper) {
        this.attachments = attachments;
        this.mapper = mapper;
    }

    public GatewayData.Attachment create(GatewayData.Attachment data) {
        return mapper.attachment(attachments.save(mapper.attachment(data)));
    }

    public void store(String id, String token, Instant now, byte[] contents) {
        var attachment = attachments.lockById(id)
                .filter(row -> row.expires.isAfter(now) && row.uploadToken.equals(token))
                .orElseThrow(() -> ApiException.missing("Upload URL is invalid or expired"));
        attachment.contents = contents;
    }

    public void purgeExpired(Instant now) {
        attachments.deleteExpired(now);
    }

    public Optional<GatewayData.Attachment> find(String id, Instant now) {
        return attachments.findById(id).filter(row -> row.expires.isAfter(now) && row.contents != null)
                .map(mapper::attachment);
    }
}
