package com.relayforge.relayforge.service;

import com.relayforge.relayforge.entity.WebhookDelivery;
import com.relayforge.relayforge.entity.WebhookEndpoint;
import com.relayforge.relayforge.entity.WebhookEvent;
import com.relayforge.relayforge.repository.WebhookDeliveryRepository;
import com.relayforge.relayforge.repository.WebhookEndpointRepository;
import com.relayforge.relayforge.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookDeliveryWorkerTests {

    private DeliveryJobService jobService;
    private WebhookEventRepository eventRepository;
    private WebhookEndpointRepository endpointRepository;
    private WebhookDeliveryRepository deliveryRepository;
    private WebhookDestinationValidator validator;
    private WebhookHttpSender httpSender;
    private WebhookDeliveryWorker worker;

    @BeforeEach
    void setUp() throws Exception {
        jobService = mock(DeliveryJobService.class);
        eventRepository = mock(WebhookEventRepository.class);
        endpointRepository = mock(WebhookEndpointRepository.class);
        deliveryRepository = mock(WebhookDeliveryRepository.class);
        validator = mock(WebhookDestinationValidator.class);
        httpSender = mock(WebhookHttpSender.class);
        worker = new WebhookDeliveryWorker(jobService, eventRepository,
                endpointRepository, deliveryRepository, validator, httpSender);
        when(validator.validate("https://hooks.example.test/receive"))
                .thenReturn(URI.create("https://hooks.example.test/receive"));
        when(jobService.recordAttemptStarted(any())).thenReturn(true);
    }

    @Test
    void successful2xxResponseMarksDeliveryDeliveredAndRecordsAttempt() throws Exception {
        Fixture fixture = fixture(true);
        when(jobService.claimReadyDeliveries()).thenReturn(List.of(fixture.claim()));
        when(httpSender.postJson(any(), any(), any(), any(), any())).thenReturn(204);

        worker.poll();

        verify(jobService).recordAttemptStarted(fixture.claim());
        verify(jobService).complete(fixture.claim(), "DELIVERED", 204, null);
    }

    @Test
    void non2xxResponseMarksDeliveryFailedWithResponseCode() throws Exception {
        Fixture fixture = fixture(true);
        when(jobService.claimReadyDeliveries()).thenReturn(List.of(fixture.claim()));
        when(httpSender.postJson(any(), any(), any(), any(), any())).thenReturn(503);

        worker.poll();

        verify(jobService).scheduleRetry(fixture.claim(), 503,
                "Receiver returned HTTP 503", 60);
    }

    @Test
    void connectionFailureMarksDeliveryFailed() throws Exception {
        Fixture fixture = fixture(true);
        when(jobService.claimReadyDeliveries()).thenReturn(List.of(fixture.claim()));
        when(httpSender.postJson(any(), any(), any(), any(), any())).thenThrow(new ConnectException());

        worker.poll();

        verify(jobService).scheduleRetry(fixture.claim(), null, "Connection failed", 60);
    }

    @Test
    void inactiveEndpointDoesNotReceiveRequest() throws Exception {
        Fixture fixture = fixture(false);
        when(jobService.claimReadyDeliveries()).thenReturn(List.of(fixture.claim()));

        worker.poll();

        verify(httpSender, never()).postJson(any(), any(), any(), any(), any());
        verify(jobService).complete(fixture.claim(), "CANCELLED", null, "Endpoint is inactive");
    }

    @Test
    void oneFailedDeliveryDoesNotStopTheNextDelivery() throws Exception {
        Fixture first = fixture(true);
        Fixture second = fixture(true);
        when(jobService.claimReadyDeliveries())
                .thenReturn(List.of(first.claim(), second.claim()));
        Map<UUID, WebhookDelivery> deliveries = Map.of(
                first.claim().id(), first.delivery(),
                second.claim().id(), second.delivery());
        Map<UUID, WebhookEvent> events = Map.of(
                first.event().getId(), first.event(),
                second.event().getId(), second.event());
        Map<UUID, WebhookEndpoint> endpoints = Map.of(
                first.endpoint().getId(), first.endpoint(),
                second.endpoint().getId(), second.endpoint());
        when(deliveryRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(deliveries.get(invocation.getArgument(0))));
        when(eventRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(events.get(invocation.getArgument(0))));
        when(endpointRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(endpoints.get(invocation.getArgument(0))));
        when(httpSender.postJson(any(), any(), any(), any(), any()))
                .thenThrow(new ConnectException())
                .thenReturn(202);

        worker.poll();

        org.mockito.ArgumentCaptor<DeliveryJobService.ClaimedDelivery> retryClaim = org.mockito.ArgumentCaptor.forClass(DeliveryJobService.ClaimedDelivery.class);
        verify(jobService).scheduleRetry(retryClaim.capture(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("Connection failed"), org.mockito.ArgumentMatchers.eq(60));
        org.mockito.ArgumentCaptor<DeliveryJobService.ClaimedDelivery> successClaim = org.mockito.ArgumentCaptor.forClass(DeliveryJobService.ClaimedDelivery.class);
        verify(jobService).complete(successClaim.capture(), org.mockito.ArgumentMatchers.eq("DELIVERED"), org.mockito.ArgumentMatchers.eq(202), org.mockito.ArgumentMatchers.isNull());
        org.junit.jupiter.api.Assertions.assertNotEquals(retryClaim.getValue().id(), successClaim.getValue().id());
    }

    private Fixture fixture(boolean active) {
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        DeliveryJobService.ClaimedDelivery claim =
                new DeliveryJobService.ClaimedDelivery(UUID.randomUUID(), UUID.randomUUID());
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(claim.id());
        delivery.setEventId(eventId);
        delivery.setEndpointId(endpointId);

        WebhookEvent event = new WebhookEvent();
        event.setId(eventId);
        event.setPayload("{\"type\":\"test\"}");

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(endpointId);
        endpoint.setUrl("https://hooks.example.test/receive");
        endpoint.setActive(active);

        when(deliveryRepository.findById(claim.id())).thenReturn(Optional.of(delivery));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(endpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));
        return new Fixture(claim, delivery, event, endpoint);
    }

    private record Fixture(
            DeliveryJobService.ClaimedDelivery claim,
            WebhookDelivery delivery,
            WebhookEvent event,
            WebhookEndpoint endpoint) {
    }
}
