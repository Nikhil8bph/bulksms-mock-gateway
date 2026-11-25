package com.example.bulksmsmockgateway.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Lob;

@Entity
@Table(name = "sms_attachment")
public class AttachmentEntity {
    @Id
    public String id;
    public String uploadToken;
    public String extension;
    public String mediaType;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant created;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant expires;
    @Lob
    @Column(columnDefinition = "longblob")
    public byte[] contents;
}
