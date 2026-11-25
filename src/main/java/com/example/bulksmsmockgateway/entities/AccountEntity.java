package com.example.bulksmsmockgateway.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "sms_account")
public class AccountEntity {
    @Id
    public Long id;
    public String username;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant created;
    public BigDecimal balance;
    public int creditLimit;
    public int dailyQuota;
    public boolean transferAllowed;
}
