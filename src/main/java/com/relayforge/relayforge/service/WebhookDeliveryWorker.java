package com.relayforge.relayforge.service;

import com.relayforge.relayforge.entity.WebhookEndpoint;
import com.relayforge.relayforge.entity.WebhookDelivery;
import com.relayforge.relayforge.entity.WebhookEvent;
import com.relayforge.relayforge.repository.WebhookDeliveryRepository;
import com.relayforge.relayforge.repository.WebhookEndpointRepository;
import com.relayforge.relayforge.repository.WebhookEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@ConditionalOnProperty(name = "relayforge.delivery.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDeliveryWorker {

    private final DeliveryJobService jobService;
    private final WebhookEventRepository eventRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDestinationValidator destinationValidator;
    private final WebhookHttpSender httpSender;
    private final SecretEncryptionService encryptionService;

    public WebhookDeliveryWorker(
            DeliveryJobService jobService,
            WebhookEventRepository eventRepository,
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            WebhookDestinationValidator destinationValidator,
            WebhookHttpSender httpSender) {
        this(jobService,eventRepository,endpointRepository,deliveryRepository,destinationValidator,httpSender,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WebhookDeliveryWorker(DeliveryJobService jobService, WebhookEventRepository eventRepository,
            WebhookEndpointRepository endpointRepository, WebhookDeliveryRepository deliveryRepository,
            WebhookDestinationValidator destinationValidator, WebhookHttpSender httpSender,
            SecretEncryptionService encryptionService) {
        this.jobService = jobService;
        this.eventRepository = eventRepository;
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.destinationValidator = destinationValidator;
        this.httpSender = httpSender;
        this.encryptionService = encryptionService;
    }

    @Scheduled(fixedDelayString = "${relayforge.delivery.poll-interval-ms:5000}")
    public void poll() {
        List<DeliveryJobService.ClaimedDelivery> claims = jobService.claimReadyDeliveries();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> results = new ArrayList<>();
            for (DeliveryJobService.ClaimedDelivery claim : claims) {
                results.add(executor.submit(() -> processClaim(claim)));
            }
            for (Future<?> result : results) {
                try {
                    result.get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException ignored) {
                    // A failed claim must not prevent the remaining claims from finishing.
                }
            }
        }
    }

    private void processClaim(DeliveryJobService.ClaimedDelivery claim) {
        try {
            process(claim);
        } catch (Exception exception) {
            try {
                jobService.complete(claim, "FAILED", null, errorMessage(exception));
            } catch (Exception ignored) {
                // Keep this polling pass moving if persistence for one claim fails.
            }
        }
    }

    private void process(DeliveryJobService.ClaimedDelivery claim) throws Exception {
        WebhookDelivery delivery = deliveryRepository.findById(claim.id()).orElse(null);
        if (delivery == null) {
            jobService.complete(claim, "FAILED", null, "Delivery record not found");
            return;
        }

        WebhookEvent event = eventRepository.findById(delivery.getEventId()).orElse(null);
        if (event == null) {
            jobService.complete(claim, "FAILED", null, "Associated event not found");
            return;
        }

        WebhookEndpoint endpoint = endpointRepository.findById(
                        delivery.getEndpointId())
                .orElse(null);
        if (endpoint == null) {
            jobService.complete(claim, "FAILED", null, "Associated endpoint not found");
            return;
        }
        if (!endpoint.isActive()) {
            jobService.complete(claim, "CANCELLED", null, "Endpoint is inactive");
            return;
        }

        URI uri = destinationValidator.validate(endpoint.getUrl());
        if (!jobService.recordAttemptStarted(claim)) {
            return;
        }

        try {
            String signingSecret = endpoint.isSigningEnabled() && encryptionService != null
                    ? encryptionService.decrypt(endpoint.getSigningSecretEncrypted()) : null;
            int responseStatus = httpSender.postJson(uri, event.getPayload(), event.getId(), delivery.getId(), signingSecret);
            if (responseStatus >= 200 && responseStatus < 300) {
                jobService.complete(claim, "DELIVERED", responseStatus, null);
            } else if (responseStatus == 408 || responseStatus == 429 || responseStatus >= 500) {
                jobService.scheduleRetry(claim, responseStatus,
                        "Receiver returned HTTP " + responseStatus, delivery.getRetryIntervalSeconds());
            } else {
                jobService.complete(claim, "FAILED", responseStatus,
                        "Receiver returned HTTP " + responseStatus);
            }
        } catch (Exception exception) {
            if (exception instanceof HttpTimeoutException || exception instanceof ConnectException
                    || exception instanceof UnknownHostException) {
                jobService.scheduleRetry(claim, null, errorMessage(exception), delivery.getRetryIntervalSeconds());
            } else {
                jobService.complete(claim, "FAILED", null, errorMessage(exception));
            }
        }
    }

    private String errorMessage(Exception exception) {
        if (exception instanceof HttpTimeoutException
                || exception instanceof HttpConnectTimeoutException) {
            return "HTTP request timed out";
        }
        if (exception instanceof ConnectException) {
            return "Connection failed";
        }
        if (exception instanceof UnknownHostException) {
            return "Endpoint host could not be resolved";
        }
        if (exception instanceof IllegalArgumentException) {
            return exception.getMessage();
        }
        return "Delivery failed (" + exception.getClass().getSimpleName() + ")";
    }
}
