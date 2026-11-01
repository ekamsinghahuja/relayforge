
package com.relayforge.relayforge.controller;

import com.relayforge.relayforge.service.WebhookEndpointService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/endpoints")
public class WebhookEndpointController {

    private final WebhookEndpointService service;

    public WebhookEndpointController(WebhookEndpointService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookEndpointService.EndpointResponse registerEndpoint(
            @Valid @RequestBody CreateEndpointRequest request) {
        return service.registerEndpoint(
                request.url(),
                request.name(), request.description(), Boolean.TRUE.equals(request.signingEnabled()), request.signingSecret());
    }

    @GetMapping
    public List<WebhookEndpointService.EndpointResponse> getAllEndpoints() {
        return service.getAllEndpoints();
    }

    @GetMapping("/{id}")
    public WebhookEndpointService.EndpointResponse getEndpointById(@PathVariable UUID id) {
        return service.getEndpointById(id);
    }

    @PatchMapping("/{id}/status")
    public WebhookEndpointService.EndpointResponse updateEndpointStatus(
            @PathVariable UUID id,
            @RequestBody UpdateEndpointStatusRequest request) {
        return service.updateEndpointStatus(id, request.active());
    }

    @PostMapping("/{id}/status")
    public WebhookEndpointService.EndpointResponse updateEndpointStatusPost(
            @PathVariable UUID id, @RequestBody UpdateEndpointStatusRequest request) {
        return service.updateEndpointStatus(id, request.active());
    }

    public record UpdateEndpointStatusRequest(boolean active) {
    }

    public record CreateEndpointRequest(
            @NotBlank @Size(max = 2048) String url,
            @Size(max = 255) String name,
            @Size(max = 255) String description,
            Boolean signingEnabled,
            @Size(max = 4096) String signingSecret) {
    }
}
