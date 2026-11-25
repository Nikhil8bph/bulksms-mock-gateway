package com.example.bulksmsmockgateway.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

@ConfigurationProperties("sandbox")
public record SandboxSettings(String tokenId, String tokenSecret, String emailFrom, String emailTo,
                              URI publicBaseUrl, List<URI> webhookOrigins) {
    public SandboxSettings {
        required(tokenId, "BULKSMS_TOKEN_ID");
        required(tokenSecret, "BULKSMS_TOKEN_SECRET");
        if (tokenId.contains(":")) {
            throw new IllegalArgumentException("BULKSMS_TOKEN_ID cannot contain a colon");
        }
        validateEmail(emailFrom, "SMS_EMAIL_FROM");
        validateEmail(emailTo, "SMS_EMAIL_TO");
        if (publicBaseUrl == null || publicBaseUrl.getHost() == null || publicBaseUrl.getUserInfo() != null
                || publicBaseUrl.getQuery() != null || publicBaseUrl.getFragment() != null
                || !List.of("http", "https").contains(publicBaseUrl.getScheme())) {
            throw new IllegalArgumentException("SANDBOX_PUBLIC_BASE_URL must be an absolute HTTP or HTTPS URL");
        }
        webhookOrigins = webhookOrigins == null ? List.of() : List.copyOf(webhookOrigins);
    }

    public static void required(String value, String variable) {
        if (value == null || value.isBlank() || value.equals("${" + variable + "}")) {
            throw new IllegalArgumentException(variable + " is required");
        }
    }

    public static void validateEmail(String value, String variable) {
        required(value, variable);
        try {
            var address = new InternetAddress(value, true);
            address.validate();
            if (!value.equals(address.getAddress()) || !value.contains("@")) {
                throw new AddressException();
            }
        } catch (AddressException ex) {
            throw new IllegalArgumentException(variable + " must be a single email address");
        }
    }
}
