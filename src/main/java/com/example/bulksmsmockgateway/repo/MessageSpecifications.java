package com.example.bulksmsmockgateway.repo;


import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.entities.MessageEntity;
import com.example.bulksmsmockgateway.exception.ApiException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;

public final class MessageSpecifications {
    private static final Pattern CLAUSE = Pattern.compile("([a-zA-Z.]+)(<=|>=|=|<|>)(.+)");
    private static final Pattern SUBMISSION = Pattern.compile("1-([0-9]+)");
    private static final Set<String> STATUS_TYPES = Set.of("ACCEPTED", "SENT", "DELIVERED", "FAILED", "UNKNOWN", "SCHEDULED");
    private static final Set<String> FAILED_SUBTYPES = Set.of("EXPIRED", "HANDSET_ERROR", "BLOCKED", "NOT_SENT");

    private MessageSpecifications() {}

    public static Specification<MessageEntity> matching(GatewayData.MessageQuery request) {
        Specification<MessageEntity> spec = (root, query, cb) -> cb.lessThanOrEqualTo(root.get("availableAt"), request.now());
        if (request.filter() != null) {
            for (String clause : request.filter().split("&", -1)) {
                spec = spec.and(parse(clause));
            }
        }
        return spec;
    }

    private static Specification<MessageEntity> parse(String clause) {
        var match = CLAUSE.matcher(clause);
        if (!match.matches()) {
            throw ApiException.invalid("Invalid filter clause");
        }
        String operator = match.group(2);
        String value = match.group(3);
        return switch (match.group(1)) {
            case "id" -> comparable("id", operator, numericId(value, 0));
            case "submission.date" -> comparable("submittedAt", operator, date(value));
            case "type" -> comparable("direction", equality(operator), allowed(value, Set.of("SENT", "RECEIVED")));
            case "status.type" -> comparable("statusType", equality(operator), allowed(value, STATUS_TYPES));
            case "status.id" -> status(equality(operator), value);
            case "submission.id" -> comparable("submissionId", equality(operator), submission(value));
            case "userSuppliedId" -> {
                equality(operator);
                yield exactUserSuppliedId(value);
            }
            default -> throw ApiException.invalid("Unknown message filter field");
        };
    }

    private static Specification<MessageEntity> exactUserSuppliedId(String value) {
        // A binary comparison also preserves trailing spaces under MySQL's padded collations.
        return (root, query, cb) -> cb.equal(root.get("userSuppliedId").cast(byte[].class),
                cb.literal(value).cast(byte[].class));
    }

    private static Specification<MessageEntity> status(String operator, String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 2) {
            throw ApiException.invalid("Invalid status.id filter");
        }
        var type = comparable("statusType", operator, allowed(parts[0], STATUS_TYPES));
        if (parts[1].equals("null")) {
            return type.and((root, query, cb) -> cb.isNull(root.get("statusSubtype")));
        }
        if (!parts[0].equals("FAILED")) {
            throw ApiException.invalid("A status subtype requires FAILED");
        }
        return type.and(comparable("statusSubtype", operator, allowed(parts[1], FAILED_SUBTYPES)));
    }

    private static <V extends Comparable<? super V>> Specification<MessageEntity> comparable(String field, String operator, V value) {
        return (root, query, cb) -> compare(cb, root.get(field), operator, value);
    }

    private static <V extends Comparable<? super V>> Predicate compare(CriteriaBuilder cb, Expression<V> field,
                                                                       String operator, V value) {
        return switch (operator) {
            case "=" -> cb.equal(field, value);
            case "<" -> cb.lessThan(field, value);
            case ">" -> cb.greaterThan(field, value);
            case "<=" -> cb.lessThanOrEqualTo(field, value);
            case ">=" -> cb.greaterThanOrEqualTo(field, value);
            default -> throw new IllegalArgumentException("Unknown comparison operator");
        };
    }

    private static String equality(String operator) {
        if (!"=".equals(operator)) {
            throw ApiException.invalid("This message filter only supports equality");
        }
        return operator;
    }

    private static String allowed(String value, Set<String> choices) {
        if (!choices.contains(value)) {
            throw ApiException.invalid("Invalid message filter value");
        }
        return value;
    }

    private static long numericId(String value, long minimum) {
        try {
            long number = Long.parseLong(value);
            if (number >= minimum) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // Use the same API error for malformed and out-of-range identifiers.
        }
        throw ApiException.invalid("Invalid message filter ID");
    }

    private static long submission(String value) {
        var match = SUBMISSION.matcher(value);
        if (!match.matches()) {
            throw ApiException.invalid("Invalid submission ID");
        }
        return numericId(match.group(1), 1);
    }

    private static Instant date(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ex) {
            throw ApiException.invalid("Invalid submission.date filter");
        }
    }
}
