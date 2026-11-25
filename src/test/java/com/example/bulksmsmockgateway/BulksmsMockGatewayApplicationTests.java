package com.example.bulksmsmockgateway;

import com.example.bulksmsmockgateway.clients.WebhookClient;
import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sandbox;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.mail.password=test-app-password",
        "sandbox.email-from=from@example.test", "sandbox.email-to=to@example.test",
        "sandbox.token-id=local-token", "sandbox.token-secret=local-secret",
        "sandbox.dispatch-delay=100",
        "RENDER_EXTERNAL_URL=https://gateway-example.onrender.com"
})
class BulksmsMockGatewayApplicationTests {
    private static final String AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("local-token:local-secret".getBytes(StandardCharsets.UTF_8));

    @Autowired
    WebApplicationContext context;
    @Autowired
    Filter springSecurityFilterChain;
    @Autowired
    ObjectMapper json;
    @MockitoBean
    JavaMailSender mail;
    @MockitoBean
    WebhookClient webhooks;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        when(webhooks.validate(any())).thenReturn(new WebhookClient.Result(204, 0));
        when(webhooks.deliver(any(), any())).thenReturn(new WebhookClient.Result(200, 0));
    }

    @Test
    void servesSwaggerUiAndUsesTheLocalContractWhenPresent() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("swagger-ui-bundle.js")));
        mvc.perform(get("/swagger-ui/sandbox.js"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("../v3/api-docs")));
        mvc.perform(get("/webjars/swagger-ui/swagger-ui.css")).andExpect(status().isOk());
        mvc.perform(get("/webjars/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        var apiDocs = mvc.perform(get("/v3/api-docs"));
        if (!new ClassPathResource("bulksms-api-specification-docs.yaml").exists()) {
            apiDocs.andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("bulksms-api-specification-docs.yaml")));
            return;
        }
        apiDocs.andExpect(status().isOk())
                .andExpect(jsonPath("$.servers.length()").value(1))
                .andExpect(jsonPath("$.servers[0].url").value("/v1"))
                .andExpect(jsonPath("$.paths['/messages'].post.operationId").value("message.send"))
                .andExpect(jsonPath("$.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"));
    }

    @Test
    void reportsDatabaseReadinessWithoutAuthentication() throws Exception {
        mvc.perform(get("/healthz"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void protectsEndpointsWithBasicTokenAuthentication() throws Exception {
        mvc.perform(get("/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"BulkSMS\", charset=\"UTF-8\""))
                .andExpect(jsonPath("$.type").value("https://developer.bulksms.com/json/v1/errors/#authentication-required"));
        mvc.perform(get("/v1/profile").header("Authorization", "Basic YTpi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://developer.bulksms.com/json/v1/errors/#authentication-failed"));
        mvc.perform(get("/v1/profile").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("sandbox"))
                .andExpect(jsonPath("$.credits.isTransferAllowed").value(true));
    }

    @Test
    void persistsRendersAndDeliversAnSmsOnceForADeduplicationKey() throws Exception {
        String payload = """
                [{"to":{"address":"+447700900101","fields":["Alice"]},
                  "from":{"type":"REPLIABLE"},"body":"Hi {F0#} 😀","userSuppliedId":"mail-check"}]
                """;
        byte[] first = mvc.perform(post("/v1/messages").header("Authorization", AUTH)
                        .param("auto-unicode", "true").param("deduplication-id", "31")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].encoding").value("UNICODE"))
                .andExpect(jsonPath("$[0].body").value("Hi Alice 😀"))
                .andExpect(jsonPath("$[0].status.id").value("ACCEPTED.null"))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode message = json.readTree(first).get(0);
        String id = message.path("id").asString();

        verify(mail, timeout(7000)).send(argThat((SimpleMailMessage email) ->
                ("BulkSMS sandbox message " + id).equals(email.getSubject())
                        && "from@example.test".equals(email.getFrom())
                        && email.getTo() != null && email.getTo().length == 1
                        && "to@example.test".equals(email.getTo()[0])
                        && email.getText().contains("Hi Alice 😀")));
        await().atMost(7, TimeUnit.SECONDS).untilAsserted(() ->
                mvc.perform(get("/v1/messages/{id}", id).header("Authorization", AUTH))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.status.id").value("DELIVERED.null")));
        mvc.perform(get("/v1/messages/send").header("Authorization", AUTH)
                        .param("to", "+447700900777").param("body", "ignored").param("deduplication-id", "31"))
                .andExpect(status().isCreated()).andExpect(content().bytes(first));
        mvc.perform(get("/v1/messages").header("Authorization", AUTH)
                        .param("sortOrder", "ASCENDING").param("limit", "1")
                        .param("filter", "id>0&submission.id=" + message.path("submission").path("id").asString()
                                + "&status.id=DELIVERED.null&userSuppliedId=mail-check"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(post("/sandbox/inbound-messages").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"+447700900101\",\"to\":\"+447700900000\",\"body\":\"Reply\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.relatedSentMessageId").value(id));
        mvc.perform(get("/v1/messages/{id}/relatedReceivedMessages", id).header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("RECEIVED"));
    }

    @Test
    void schedulesMessagesAndSupportsSimpleFormSubmissionsAndBlockedNumbers() throws Exception {
        String date = Instant.now().plusSeconds(3600).toString();
        String scheduled = mvc.perform(post("/v1/messages").header("Authorization", AUTH)
                        .param("schedule-date", date).param("schedule-description", "Later")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"447700900102\",\"body\":\"Later\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].status.type").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/v1/messages/{id}", json.readTree(scheduled).get(0).path("id").asString())
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
        mvc.perform(post("/v1/blocked-numbers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("[\"447700900103\",\"+447700900103\"]"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        mvc.perform(get("/v1/blocked-numbers").header("Authorization", AUTH).param("min-id", "0").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].phoneNumber").value("447700900103"));
        mvc.perform(post("/v1/messages").header("Authorization", AUTH).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", "+447700900103").param("body", "Blocked"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].status.id").value("FAILED.BLOCKED"));
        mvc.perform(get("/v1/messages").header("Authorization", AUTH).param("filter", "invalid=1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void validatesAndInvokesWebhooksAndSupportsUpdatesAndDeletion() throws Exception {
        String request = """
                {"name":"callback","url":"http://localhost:9000/sms","triggerScope":"RECEIVED","invokeOption":"MANY"}
                """;
        byte[] created = mvc.perform(post("/v1/webhooks").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsByteArray();
        long id = json.readTree(created).path("id").asLong();
        verify(webhooks).validate(argThat(hook -> hook.url().endsWith("/sms")));
        mvc.perform(get("/v1/webhooks/{id}", id).header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/v1/webhooks").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(post("/sandbox/inbound-messages").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"+447700900111\",\"to\":\"+447700900000\",\"body\":\"Callback check\"}"))
                .andExpect(status().isCreated());
        verify(webhooks, timeout(7000)).deliver(argThat((GatewayData.WebhookJob job) -> job.webhook().id() == id),
                argThat(body -> body.contains("Callback check")));
        mvc.perform(post("/v1/webhooks/{id}", id).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(request.replace("callback", "renamed")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("renamed"));
        mvc.perform(delete("/v1/webhooks/{id}", id).header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(content().string(""));
        mvc.perform(get("/v1/webhooks/{id}", id).header("Authorization", AUTH)).andExpect(status().isNotFound());
    }

    @Test
    void persistsSignedAttachmentsAndCreditTransfers() throws Exception {
        byte[] signed = mvc.perform(post("/v1/rmm/pre-sign-attachment").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fileExtension\":\"txt\",\"mediaType\":\"text/plain\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fields[0].value").value("text/plain"))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode urls = json.readTree(signed);
        URI put = URI.create(urls.path("putUrl").asString());
        URI fetch = URI.create(urls.path("fetchUrl").asString());
        assertThat(put).hasScheme("https").hasHost("gateway-example.onrender.com");
        assertThat(fetch).hasScheme("https").hasHost("gateway-example.onrender.com");
        mvc.perform(get(fetch)).andExpect(status().isNotFound());
        mvc.perform(put(put.getPath()).param("token", "incorrect").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isNotFound());
        mvc.perform(put(put).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isOk());
        mvc.perform(get(fetch)).andExpect(status().isOk()).andExpect(content().string("hello"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"attachment.txt\""));

        var before = profile().path("credits").path("balance").decimalValue();
        mvc.perform(post("/v1/credit/transfer").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUsername\":\"sandbox-recipient\",\"toUserId\":2,\"credits\":2.5}"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertThat(profile().path("credits").path("balance").decimalValue())
                .isEqualByComparingTo(before.subtract(new java.math.BigDecimal("2.5")));
    }

    private JsonNode profile() throws Exception {
        return json.readTree(mvc.perform(get("/v1/profile").header("Authorization", AUTH))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
}
