package com.example.bulksmsmockgateway.service;

import com.example.bulksmsmockgateway.clients.GmailClient;
import com.example.bulksmsmockgateway.clients.WebhookClient;
import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.mapper.ApiMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.Filter;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:regression;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "sandbox.email-from=from@example.test", "sandbox.email-to=to@example.test",
        "sandbox.token-id=local-token", "sandbox.token-secret=local-secret",
        "sandbox.dispatch-enabled=false"
})
@Sql(statements = {
        "delete from sms_webhook_delivery", "delete from sms_webhook", "delete from sms_message",
        "delete from sms_submission", "delete from sms_blocked_number", "delete from sms_attachment"
})
class GatewayContractRegressionTests {
    private static final String AUTH = "Basic bG9jYWwtdG9rZW46bG9jYWwtc2VjcmV0";
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    @Autowired
    WebApplicationContext context;
    @Autowired
    Filter springSecurityFilterChain;
    @Autowired
    ObjectMapper json;
    @Autowired
    DeliveryTransactions deliveries;
    @Autowired
    AttachmentService attachments;
    @Autowired
    ApiMapper mapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManagerFactory entityManagerFactory;
    @MockitoBean
    Clock clock;
    @MockitoBean
    GmailClient gmail;
    @MockitoBean
    WebhookClient webhooks;

    private MockMvc mvc;
    private DeliveryWorker worker;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(webhooks.validate(any())).thenReturn(new WebhookClient.Result(204, 0));
        when(webhooks.deliver(any(), any())).thenReturn(new WebhookClient.Result(204, 0));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        worker = new DeliveryWorker(deliveries, gmail, webhooks, mapper, json, clock, attachments);
    }

    @Test
    void blocksScheduledMessagesAtDispatchAndKeepsTheOriginalCharge() throws Exception {
        createWebhook("SENT", "ONE");
        var balance = balance();
        String id = response(mvc.perform(post("/v1/messages").header("Authorization", AUTH)
                .param("schedule-date", NOW.plusSeconds(60).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"to":"+447700900201","body":"Scheduled","deliveryReports":"ERRORS"}
                        """)).andExpect(status().isCreated()).andExpect(jsonPath("$[0].status.type").value("SCHEDULED")))
                .get(0).path("id").asString();

        block("447700900201");
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        worker.dispatch();

        verifyNoInteractions(gmail);
        verify(webhooks).deliver(any(), argThat(body -> body.contains("FAILED.BLOCKED")));
        message(id).andExpect(status().isOk()).andExpect(jsonPath("$.status.id").value("FAILED.BLOCKED"))
                .andExpect(jsonPath("$.creditCost").value(1));
        assertThat(balance()).isEqualByComparingTo(balance.subtract(BigDecimal.ONE));
    }

    @Test
    void rechecksBlocklistDuringSmtpRetries() throws Exception {
        String id = response(send("447700900202", "Retry")).get(0).path("id").asString();
        var first = deliveries.claimMail(NOW).orElseThrow();
        deliveries.finishMail(first, "MailSendException", NOW);
        message(id).andExpect(jsonPath("$.status.id").value("UNKNOWN.null"));
        block("447700900202");
        when(clock.instant()).thenReturn(NOW.plusSeconds(16));

        worker.dispatch();

        verifyNoInteractions(gmail);
        message(id).andExpect(jsonPath("$.status.id").value("FAILED.BLOCKED"));
    }

    @Test
    void retriesRejectedWebhookBatchesAsIndividualCallbacks() throws Exception {
        createWebhook("RECEIVED", "MANY");
        inbound("447700900203", "Valid");
        inbound("447700900204", "Invalid");
        when(webhooks.deliver(any(), any())).thenAnswer(call -> {
            GatewayData.WebhookJob job = call.getArgument(0);
            if (job.ids().size() > 1) {
                return new WebhookClient.Result(422, 0);
            }
            return new WebhookClient.Result(job.messages().getFirst().body().equals("Valid") ? 204 : 400, 0);
        });

        worker.dispatch();
        when(clock.instant()).thenReturn(NOW.plusSeconds(100));
        worker.dispatch();

        var jobs = ArgumentCaptor.forClass(GatewayData.WebhookJob.class);
        verify(webhooks, times(3)).deliver(jobs.capture(), any());
        var calls = jobs.getAllValues();
        assertThat(calls.getFirst().messages()).hasSize(2);
        assertThat(calls.subList(1, 3)).allSatisfy(job -> {
            assertThat(job.attempt()).isEqualTo(2);
            assertThat(job.messages()).hasSize(1);
        });
        assertThat(calls.subList(1, 3)).extracting(job -> job.messages().getFirst().body())
                .containsExactlyInAnyOrder("Valid", "Invalid");
    }

    @Test
    void cancelsQueuedAndRetryingCallbacksWhenScopeChanges() throws Exception {
        long hookId = createWebhook("SENT", "ONE");
        block("447700900205");
        send("447700900205", "Will retry");
        var retry = deliveries.claimWebhook(NOW).orElseThrow();
        deliveries.finishWebhook(retry, 503, 0, NOW);
        send("447700900205", "Still queued");

        mvc.perform(post("/v1/webhooks/{id}", hookId).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(webhook("RECEIVED", "ONE")))
                .andExpect(status().isOk());
        inbound("447700900206", "Incoming after update");
        when(clock.instant()).thenReturn(NOW.plusSeconds(100));
        worker.dispatch();

        verify(webhooks, times(1)).deliver(
                argThat(job -> job.webhook().triggerScope().equals("RECEIVED")
                        && job.messages().getFirst().direction().equals("RECEIVED")),
                argThat(body -> body.contains("Incoming after update")));
    }

    @Test
    void rejectsHeadWithoutCreatingAMessageOrChargingTheAccount() throws Exception {
        var balance = balance();
        mvc.perform(head("/v1/messages/send").header("Authorization", AUTH)
                        .param("to", "+447700900207").param("body", "No send"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"));
        mvc.perform(get("/v1/messages").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(balance()).isEqualByComparingTo(balance);

        mvc.perform(get("/v1/messages/send").header("Authorization", AUTH)
                        .param("to", "+447700900207").param("body", "Send"))
                .andExpect(status().isCreated());
        worker.dispatch();
        verify(gmail).send(argThat(message -> message.body().equals("Send")));
    }

    @Test
    void honorsStopOnlyWhenItMatchesAnExistingSentMessage() throws Exception {
        inbound("447700900208", "STOP").andExpect(jsonPath("$.relatedSentMessageId").isEmpty());
        mvc.perform(get("/v1/blocked-numbers").header("Authorization", AUTH))
                .andExpect(jsonPath("$.length()").value(0));

        String id = response(mvc.perform(post("/v1/messages").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"to":"+447700900208","from":{"type":"REPLIABLE"},"body":"Opt out with STOP"}
                        """)).andExpect(status().isCreated())).get(0).path("id").asString();
        worker.dispatch();
        inbound("447700900208", "STOP").andExpect(jsonPath("$.relatedSentMessageId").value(id));
        mvc.perform(get("/v1/blocked-numbers").header("Authorization", AUTH))
                .andExpect(jsonPath("$[0].phoneNumber").value("447700900208"));
    }

    @Test
    void reservesAcceptedAndOmitsBillingFieldsInTheSubmissionSnapshot() throws Exception {
        String payload = """
                {"to":"+447700900209","body":"Pending"}
                """;
        var first = response(mvc.perform(post("/v1/messages").header("Authorization", AUTH)
                        .param("deduplication-id", "91").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].status.id").value("ACCEPTED.null"))
                .andExpect(jsonPath("$[0].numberOfParts").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[0].creditCost").doesNotHaveJsonPath()));
        String id = first.get(0).path("id").asString();
        message(id).andExpect(jsonPath("$.status.id").value("UNKNOWN.null"))
                .andExpect(jsonPath("$.numberOfParts").value(1)).andExpect(jsonPath("$.creditCost").value(1));
        mvc.perform(get("/v1/messages").header("Authorization", AUTH).param("filter", "status.type=ACCEPTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        worker.dispatch();

        var replay = response(mvc.perform(post("/v1/messages").header("Authorization", AUTH)
                        .param("deduplication-id", "91").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].numberOfParts").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[0].creditCost").doesNotHaveJsonPath()));
        assertThat(replay).isEqualTo(first);
    }

    @Test
    void validatesRecipientIdBeforeAnyCreditIsTransferred() throws Exception {
        var balance = balance();
        transfer("2.9").andExpect(status().isBadRequest());
        transfer("2.000000000000000001").andExpect(status().isBadRequest());
        assertThat(balance()).isEqualByComparingTo(balance);
        transfer("2.0").andExpect(status().isOk());
        assertThat(balance()).isEqualByComparingTo(balance.subtract(BigDecimal.ONE));
    }

    @Test
    void filtersUserSuppliedIdsExactlyWithACaseInsensitiveColumn() throws Exception {
        jdbc.execute("alter table sms_message alter column user_supplied_id varchar_ignorecase(20)");
        try {
            mvc.perform(post("/v1/messages").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    [{"to":"+447700900210","body":"Upper","userSuppliedId":"Order-A"},
                                     {"to":"+447700900210","body":"Lower","userSuppliedId":"order-a"},
                                     {"to":"+447700900210","body":"Space","userSuppliedId":"Order-A "}]
                                    """))
                    .andExpect(status().isCreated());
            assertThat(jdbc.queryForObject("select count(*) from sms_message where user_supplied_id = 'Order-A'",
                    Integer.class)).isGreaterThan(1);
            mvc.perform(get("/v1/messages").header("Authorization", AUTH).param("filter", "userSuppliedId=Order-A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].body").value("Upper"));
        } finally {
            jdbc.execute("alter table sms_message alter column user_supplied_id varchar(20)");
        }
    }

    @Test
    void purgesExpiredAttachmentsWithoutLoadingTheirEntities() {
        insertAttachment("expired", NOW);
        insertAttachment("active", NOW.plusSeconds(30));
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        attachments.purgeExpired();

        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(jdbc.queryForList("select id from sms_attachment", String.class)).containsExactly("active");
    }

    private ResultActions send(String phone, String body) throws Exception {
        return mvc.perform(post("/v1/messages").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("to", phone, "body", body))))
                .andExpect(status().isCreated());
    }

    private ResultActions inbound(String from, String body) throws Exception {
        return mvc.perform(post("/sandbox/inbound-messages").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("from", from, "to", "+447700900000", "body", body))))
                .andExpect(status().isCreated());
    }

    private ResultActions message(String id) throws Exception {
        return mvc.perform(get("/v1/messages/{id}", id).header("Authorization", AUTH)).andExpect(status().isOk());
    }

    private ResultActions transfer(String id) throws Exception {
        return mvc.perform(post("/v1/credit/transfer").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"toUsername":"sandbox-recipient","toUserId":%s,"credits":1}
                        """.formatted(id)));
    }

    private void block(String phone) throws Exception {
        mvc.perform(post("/v1/blocked-numbers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(List.of(phone))))
                .andExpect(status().isOk());
    }

    private long createWebhook(String scope, String invoke) throws Exception {
        return response(mvc.perform(post("/v1/webhooks").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                .content(webhook(scope, invoke))).andExpect(status().isOk())).path("id").asLong();
    }

    private static String webhook(String scope, String invoke) {
        return """
                {"name":"callback","url":"http://localhost:9000/sms","triggerScope":"%s","invokeOption":"%s"}
                """.formatted(scope, invoke);
    }

    private BigDecimal balance() throws Exception {
        return response(mvc.perform(get("/v1/profile").header("Authorization", AUTH))
                .andExpect(status().isOk())).path("credits").path("balance").decimalValue();
    }

    private JsonNode response(ResultActions request) throws Exception {
        return json.readTree(request.andReturn().getResponse().getContentAsByteArray());
    }

    private void insertAttachment(String id, Instant expires) {
        jdbc.update("""
                insert into sms_attachment (id, upload_token, extension, media_type, created, expires, contents)
                values (?, 'token', 'bin', 'application/octet-stream', ?, ?, ?)
                """, id, LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC),
                LocalDateTime.ofInstant(expires, ZoneOffset.UTC), new byte[] {1, 2, 3});
    }
}
