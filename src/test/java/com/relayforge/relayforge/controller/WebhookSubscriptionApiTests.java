package com.relayforge.relayforge.controller;

import com.relayforge.relayforge.common.GlobalExceptionHandler;
import com.relayforge.relayforge.entity.WebhookEndpoint;
import com.relayforge.relayforge.repository.WebhookEndpointRepository;
import com.relayforge.relayforge.service.WebhookEventService;
import com.relayforge.relayforge.service.WebhookSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "relayforge.delivery.enabled=false")
@Transactional
class WebhookSubscriptionApiTests {

    private MockMvc mockMvc;

    @Autowired
    private WebhookSubscriptionService subscriptionService;

    @Autowired
    private WebhookEventService eventService;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @BeforeEach
    void setUp() {
        endpointRepository.findAll().forEach(endpoint -> endpoint.setActive(false));
        endpointRepository.flush();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new WebhookSubscriptionController(subscriptionService),
                        new WebhookEventController(eventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void endpointCanSubscribeToManyTypesAndSeveralEndpointsCanShareOneType() throws Exception {
        WebhookEndpoint first = endpointRepository.saveAndFlush(endpoint());
        WebhookEndpoint second = endpointRepository.saveAndFlush(endpoint());

        create(first.getId(), "payment.completed").andExpect(status().isCreated());
        create(first.getId(), "payment.failed").andExpect(status().isCreated());
        create(second.getId(), "payment.completed").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/endpoints/{id}/subscriptions", first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endpointId").value(first.getId().toString()))
                .andExpect(jsonPath("$[0].eventType").value("payment.completed"))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].eventType").value("payment.failed"))
                .andExpect(jsonPath("$[2]").doesNotExist());

        mockMvc.perform(get("/api/v1/endpoints/{id}/subscriptions", second.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("payment.completed"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void duplicateSubscriptionReturnsConflictAndInvalidEventTypesAreRejected() throws Exception {
        WebhookEndpoint endpoint = endpointRepository.saveAndFlush(endpoint());

        create(endpoint.getId(), "payment.completed").andExpect(status().isCreated());
        create(endpoint.getId(), "payment.completed").andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/endpoints/{id}/subscriptions", endpoint.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/endpoints/{id}/subscriptions", endpoint.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointWithoutSubscriptionsReturnsAnEmptyList() throws Exception {
        WebhookEndpoint endpoint = endpointRepository.saveAndFlush(endpoint());

        mockMvc.perform(get("/api/v1/endpoints/{id}/subscriptions", endpoint.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void subscriptionRetryIntervalDefaultsToSixtySecondsAndCanBeUpdated() throws Exception {
        WebhookEndpoint endpoint = endpointRepository.saveAndFlush(endpoint());
        create(endpoint.getId(), "payment.completed")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retryIntervalSeconds").value(60));

        mockMvc.perform(patch("/api/v1/endpoints/{id}/subscriptions/{eventType}",
                                endpoint.getId(), "payment.completed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retryIntervalSeconds\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryIntervalSeconds").value(45));

        mockMvc.perform(patch("/api/v1/endpoints/{id}/subscriptions/{eventType}",
                                endpoint.getId(), "payment.completed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retryIntervalSeconds\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryIntervalIsSnapshottedPerDeliveryAtEventCreation() throws Exception {
        WebhookEndpoint endpoint = endpointRepository.saveAndFlush(endpoint());
        mockMvc.perform(post("/api/v1/endpoints/{id}/subscriptions", endpoint.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"payment.completed\",\"retryIntervalSeconds\":35}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retryIntervalSeconds").value(35));

        String firstEvent = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"payment.completed\",\"payload\":{\"n\":1}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstEventId = com.jayway.jsonpath.JsonPath.read(firstEvent, "$.id");
        mockMvc.perform(get("/api/v1/events/{eventId}/deliveries", firstEventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].retryIntervalSeconds").value(35));

        mockMvc.perform(patch("/api/v1/endpoints/{id}/subscriptions/{eventType}",
                                endpoint.getId(), "payment.completed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retryIntervalSeconds\":90}"))
                .andExpect(status().isOk());

        String secondEvent = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"payment.completed\",\"payload\":{\"n\":2}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondEventId = com.jayway.jsonpath.JsonPath.read(secondEvent, "$.id");
        mockMvc.perform(get("/api/v1/events/{eventId}/deliveries", secondEventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].retryIntervalSeconds").value(90));

        mockMvc.perform(get("/api/v1/events/{eventId}/deliveries", firstEventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].retryIntervalSeconds").value(35));
    }

    @Test
    void missingEndpointReturnsNotFoundForCreateAndList() throws Exception {
        UUID missingId = UUID.randomUUID();

        create(missingId, "payment.completed").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/endpoints/{id}/subscriptions", missingId))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/endpoints/{id}/subscriptions/{eventType}",
                        missingId, "payment.completed"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removingSubscriptionStopsFutureDeliveryJobCreation() throws Exception {
        WebhookEndpoint endpoint = endpointRepository.saveAndFlush(endpoint());
        create(endpoint.getId(), "payment.completed").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/endpoints/{id}/subscriptions/{eventType}",
                        endpoint.getId(), "payment.completed"))
                .andExpect(status().isNoContent());

        String response = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"payment.completed\",\"payload\":{}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String eventId = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        mockMvc.perform(get("/api/v1/events/{eventId}/deliveries", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(delete("/api/v1/endpoints/{id}/subscriptions/{eventType}",
                        endpoint.getId(), "payment.completed"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions create(UUID endpointId, String eventType)
            throws Exception {
        return mockMvc.perform(post("/api/v1/endpoints/{id}/subscriptions", endpointId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"" + eventType + "\"}"));
    }

    private WebhookEndpoint endpoint() {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setUrl("https://example.test/" + UUID.randomUUID());
        endpoint.setDescription("subscription test");
        endpoint.setActive(true);
        endpoint.setCreatedAt(OffsetDateTime.now());
        return endpoint;
    }
}
