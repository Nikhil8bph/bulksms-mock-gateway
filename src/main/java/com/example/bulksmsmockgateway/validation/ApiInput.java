package com.example.bulksmsmockgateway.validation;

import com.example.bulksmsmockgateway.exception.ApiException;
import tools.jackson.databind.JsonNode;

import java.util.Set;

public final class ApiInput {
    private ApiInput() {}

    public static String phone(String value) {
        if (value == null || !value.matches("\\+?[1-9][0-9]{6,14}")) {
            throw ApiException.invalid("Expected an international phone number of 7 to 15 digits");
        }
        return value.startsWith("+") ? value.substring(1) : value;
    }

    public static long id(String value) {
        try {
            if (value.matches("[0-9]+")) {
                long id = Long.parseLong(value);
                if (id > 0) {
                    return id;
                }
            }
        } catch (NumberFormatException ignored) {
            // Report out-of-range IDs the same way as other malformed IDs.
        }
        throw ApiException.invalid("ID must be a positive integer");
    }

    public static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw ApiException.invalid(field + " must be a string");
        }
        return node.asString();
    }

    public static String required(String value, String field, int length) {
        if (value == null || value.isBlank()) {
            throw ApiException.invalid(field + " is required");
        }
        return bounded(value, field, length);
    }

    public static String bounded(String value, String field, int length) {
        if (value != null && value.length() > length) {
            throw ApiException.invalid(field + " exceeds " + length + " characters");
        }
        return value;
    }

    public static String choice(String value, String fallback, String field, Set<String> choices) {
        if (value == null) {
            return fallback;
        }
        if (!choices.contains(value)) {
            throw ApiException.invalid("Invalid " + field);
        }
        return value;
    }
}
