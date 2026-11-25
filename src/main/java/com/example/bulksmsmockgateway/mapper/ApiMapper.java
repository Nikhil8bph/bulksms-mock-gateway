package com.example.bulksmsmockgateway.mapper;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.Instant;

@Mapper(componentModel = "spring")
public interface ApiMapper {
    @Mapping(target = "id", source = "message.id")
    @Mapping(target = "type", source = "message.direction")
    @Mapping(target = "from", source = "message.origin")
    @Mapping(target = "to", source = "message.destination")
    @Mapping(target = "submission", expression = "java(submission(message))")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "numberOfParts", source = "parts")
    @Mapping(target = "creditCost", source = "cost")
    Responses.Message message(GatewayData.Message message, Responses.Status status, Integer parts, BigDecimal cost);

    default Responses.Message message(GatewayData.Message message) {
        return message(message, status(message.statusType(), message.statusSubtype()), message.numberOfParts(), message.creditCost());
    }

    default Responses.Message submitted(GatewayData.Message message) {
        return message(message, status(message.initialStatusType(), message.initialStatusSubtype()), null, null);
    }

    default Responses.Submission submission(GatewayData.Message message) {
        return new Responses.Submission("1-%020d".formatted(message.submissionId()), message.submittedAt());
    }

    private static Responses.Status status(String type, String subtype) {
        return new Responses.Status(type + "." + subtype, type, subtype);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", defaultValue = "true")
    @Mapping(target = "onWebApp", defaultValue = "true")
    @Mapping(target = "invokeOption", defaultValue = "ONE")
    GatewayData.Webhook webhook(Requests.Webhook request);

    Responses.Webhook webhook(GatewayData.Webhook data);

    Responses.BlockedNumber blocked(GatewayData.BlockedNumber data);

    @Mapping(target = "created", source = "created")
    @Mapping(target = "toUserId", source = "recipientId")
    GatewayData.Transfer transfer(Requests.Transfer request, Long recipientId, Instant created);

    Responses.Download download(GatewayData.Attachment attachment);
}
