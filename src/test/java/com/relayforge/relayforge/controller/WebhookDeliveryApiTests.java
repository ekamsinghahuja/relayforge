package com.relayforge.relayforge.controller;

import com.relayforge.relayforge.entity.WebhookEndpoint;
import com.relayforge.relayforge.entity.WebhookSubscription;
import com.relayforge.relayforge.common.GlobalExceptionHandler;
import com.relayforge.relayforge.repository.WebhookEndpointRepository;
import com.relayforge.relayforge.repository.WebhookSubscriptionRepository;
import com.relayforge.relayforge.service.WebhookEventService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "relayforge.delivery.enabled=false")
@Transactional
class WebhookDeliveryApiTests {

    private MockMvc mockMvc;

    @Autowired
    private WebhookEventService eventService;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Autowired
    private WebhookSubscriptionRepository subscriptionRepository;

    @BeforeEach
    void setUp() {
        endpointRepository.findAll().forEach(endpoint -> endpoint.setActive(false));
        endpointRepository.flush();
        mockMvc = MockMvcBuilders.standaloneSetup(new WebhookEventController(eventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void eventCreatesDeliveriesForActiveEndpointsOnlyAndTheyCanBeRetrieved() throws Exception {
        WebhookEndpoint active = endpoint(true);
        WebhookEndpoint inactive = endpoint(false);
        WebhookEndpoint subscribedToDifferentType = endpoint(true);
        endpointRepository.saveAndFlush(active);
        endpointRepository.saveAndFlush(inactive);
        endpointRepository.saveAndFlush(subscribedToDifferentType);
        subscribe(active, "order.created");
        subscribe(inactive, "order.created");
        subscribe(subscribedToDifferentType, "order.deleted");

        String eventResponse = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order.created\",\"payload\":{\"id\":1}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String eventId = com.jayway.jsonpath.JsonPath.read(eventResponse, "$.id");

        mockMvc.perform(get("/api/v1/events/{eventId}/deliveries", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].endpointId").value(active.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].attemptCount").value(0))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void eventWithNoActiveEndpointsPersistsAndReturnsEmptyDeliveries() throws Exception {
        WebhookEndpoint inactive = endpoint(false);
        endpointRepository.saveAndFlush(inactive);
        subscribe(inactive, "order.created");

        String eventResponse = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order.created\",\"payload\":{}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String eventId = com.jayway.jsonpath.JsonPath.read(eventResponse, "$.id");

        mockMvc.perform(get("/api/v1/events/{eventId}/deliveries", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void nonexistentEventReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventId}/deliveries", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private WebhookEndpoint endpoint(boolean active) {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setUrl("https://example.test/webhook");
        endpoint.setDescription("test");
        endpoint.setActive(active);
        endpoint.setCreatedAt(OffsetDateTime.now());
        return endpoint;
    }

    private void subscribe(WebhookEndpoint endpoint, String eventType) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setEndpointId(endpoint.getId());
        subscription.setEventType(eventType);
        subscription.setCreatedAt(OffsetDateTime.now());
        subscriptionRepository.saveAndFlush(subscription);
    }
}
