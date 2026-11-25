package com.example.bulksmsmockgateway.mapper;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.entities.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PersistenceMapper {
    GatewayData.Message message(MessageEntity entity);

    @Mapping(target = "mailDue", ignore = true)
    @Mapping(target = "mailLease", ignore = true)
    @Mapping(target = "mailAttempts", ignore = true)
    @Mapping(target = "mailError", ignore = true)
    MessageEntity message(GatewayData.Message data);

    GatewayData.Submission submission(SubmissionEntity entity);

    @Mapping(target = "deduplicationId", ignore = true)
    SubmissionEntity submission(GatewayData.Submission data);

    GatewayData.Webhook webhook(WebhookEntity entity);

    WebhookEntity webhook(GatewayData.Webhook data);

    GatewayData.BlockedNumber blocked(BlockedNumberEntity entity);

    BlockedNumberEntity blocked(GatewayData.BlockedNumber data);

    GatewayData.Account account(AccountEntity entity);

    @Mapping(target = "id", ignore = true)
    TransferEntity transfer(GatewayData.Transfer data);

    GatewayData.Attachment attachment(AttachmentEntity entity);

    AttachmentEntity attachment(GatewayData.Attachment data);

    GatewayData.Contact contact(ContactEntity entity);
}
