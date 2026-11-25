package com.example.bulksmsmockgateway.clients;

import com.example.bulksmsmockgateway.config.SandboxSettings;
import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.exception.ApiException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class WebhookClient {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "[::1]");
    private final SandboxSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public WebhookClient(SandboxSettings settings) {
        this.settings = settings;
    }

    public URI allowed(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw ApiException.invalid("Invalid webhook URL");
            }
            if (!LOOPBACK.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && settings.webhookOrigins().stream().noneMatch(allowed -> sameOrigin(allowed, uri))) {
                throw ApiException.invalid("Webhook origin is outside the sandbox allowlist");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw ApiException.invalid("Invalid webhook URL");
        }
    }

    public Result validate(GatewayData.Webhook webhook) {
        return post(allowed(webhook.url()), "[]", Map.of());
    }

    public Result deliver(GatewayData.WebhookJob job, String json) {
        String event = job.webhook().triggerScope().equals("RECEIVED") ? "incoming-message" : "status-report";
        return post(allowed(job.webhook().url()), json, Map.of(
                "X-BulkSMS-Event", event,
                "X-BulkSMS-Webhook-Id", job.webhook().id().toString(),
                "X-BulkSMS-Delivery", job.ids().getFirst().toString(),
                "X-BulkSMS-Delivery-Attempt", Integer.toString(job.attempt())));
    }

    private Result post(URI url, String body, Map<String, String> headers) {
        var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").header("User-Agent", "BulkSMS Invoker")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        try {
            var response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
            return new Result(response.statusCode(), retryAfter(response.headers()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(0, 0);
        } catch (IOException ex) {
            return new Result(0, 0);
        }
    }

    private static boolean sameOrigin(URI a, URI b) {
        return b.getHost().equalsIgnoreCase(a.getHost()) && b.getScheme().equalsIgnoreCase(a.getScheme()) && port(a) == port(b);
    }

    private static int port(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static int retryAfter(HttpHeaders headers) {
        try {
            return Math.clamp(headers.firstValueAsLong("Retry-After").orElse(0), 0, 86_400);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public record Result(int status, int retryAfter) {}
}
