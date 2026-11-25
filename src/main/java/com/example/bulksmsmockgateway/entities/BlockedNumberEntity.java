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
@Table(name = "sms_blocked_number")
public class BlockedNumberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String phoneNumber;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant created;
    public String description;
}
