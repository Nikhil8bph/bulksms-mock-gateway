package com.example.bulksmsmockgateway.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.math.BigDecimal;
import jakarta.persistence.Column;

@Entity
@Table(name = "sms_message")
public class MessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long submissionId;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant submittedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant availableAt;
    public String direction;
    public String origin;
    public String destination;
    @Column(columnDefinition = "text")
    public String body;
    public String encoding;
    public int protocolId;
    public int messageClass;
    public int numberOfParts;
    public BigDecimal creditCost;
    public String statusType;
    public String statusSubtype;
    public Long relatedSentMessageId;
    public String userSuppliedId;
    public boolean repliable;
    public String routingGroup;
    public String deliveryReports;
    public String initialStatusType;
    public String initialStatusSubtype;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant mailDue;
    public String mailLease;
    public int mailAttempts;
    public String mailError;
}
