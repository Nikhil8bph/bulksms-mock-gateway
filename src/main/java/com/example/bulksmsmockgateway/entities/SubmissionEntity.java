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
@Table(name = "sms_submission")
public class SubmissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant submittedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant availableAt;
    public String description;
    public Integer deduplicationId;
}
