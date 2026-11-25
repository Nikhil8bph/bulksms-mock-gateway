package com.example.bulksmsmockgateway.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "sms_webhook_delivery")
public class WebhookDeliveryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long webhookId;
    public Long messageId;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant created;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant due;
    public String lease;
    public int attempt;
    public Integer lastStatus;
}
