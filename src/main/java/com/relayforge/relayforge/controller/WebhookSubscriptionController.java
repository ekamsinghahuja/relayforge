package com.relayforge.relayforge.controller;

import com.relayforge.relayforge.service.WebhookSubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endpoints/{endpointId}/subscriptions")
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService service;

    public WebhookSubscriptionController(WebhookSubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookSubscriptionService.SubscriptionResponse createSubscription(
            @PathVariable UUID endpointId,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return service.createSubscription(endpointId, request.eventType(), request.retryIntervalSeconds());
    }

    @GetMapping
    public List<WebhookSubscriptionService.SubscriptionResponse> getSubscriptions(
            @PathVariable UUID endpointId) {
        return service.getSubscriptions(endpointId);
    }

    @PatchMapping("/{eventType}")
    public WebhookSubscriptionService.SubscriptionResponse updateRetryInterval(
            @PathVariable UUID endpointId,
            @PathVariable String eventType,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        return service.updateRetryInterval(endpointId, eventType, request.retryIntervalSeconds());
    }

    @DeleteMapping("/{eventType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSubscription(
            @PathVariable UUID endpointId,
            @PathVariable String eventType) {
        service.deleteSubscription(endpointId, eventType);
    }

    public record CreateSubscriptionRequest(
            @NotBlank @Size(max = 100) String eventType,
            @Min(1) @Max(604800) Integer retryIntervalSeconds) {
    }

    public record UpdateSubscriptionRequest(
            @Min(1) @Max(604800) int retryIntervalSeconds) {
    }
}
