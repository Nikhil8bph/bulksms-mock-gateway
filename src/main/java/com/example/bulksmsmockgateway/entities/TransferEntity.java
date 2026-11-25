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

@Entity
@Table(name = "sms_transfer")
public class TransferEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String toUsername;
    public Long toUserId;
    public BigDecimal credits;
    public String commentOnFrom;
    public String commentOnTo;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant created;
}
