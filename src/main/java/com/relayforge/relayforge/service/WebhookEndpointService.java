
package com.relayforge.relayforge.service;

import com.relayforge.relayforge.common.EndpointNotFoundException;
import com.relayforge.relayforge.entity.WebhookEndpoint;
import com.relayforge.relayforge.repository.WebhookEndpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookEndpointService {

    private final WebhookEndpointRepository repository;
    private final WebhookDestinationValidator destinationValidator;
    private final SecretEncryptionService encryptionService;

    public WebhookEndpointService(WebhookEndpointRepository repository, WebhookDestinationValidator destinationValidator, SecretEncryptionService encryptionService) {
        this.repository = repository;
        this.destinationValidator = destinationValidator;
        this.encryptionService = encryptionService;
    }

    @Transactional
    public EndpointResponse registerEndpoint(
            String url, String name, String description, boolean signingEnabled, String signingSecret) {

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid endpoint URL");
        }

        String scheme = uri.getScheme();

        if (!uri.isAbsolute()
                || uri.getHost() == null
                || !("https".equalsIgnoreCase(scheme)
                        || "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(
                    "Endpoint URL must be an absolute HTTP or HTTPS URL");
        }
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("Endpoint URL must not contain user information");
        try { destinationValidator.validate(url); }
        catch (java.net.UnknownHostException ex) { throw new IllegalArgumentException("Endpoint hostname could not be resolved"); }

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setUrl(url);
        endpoint.setName(name == null || name.isBlank() ? fallbackName(uri) : name.trim());
        endpoint.setDescription(description);
        endpoint.setActive(true);
        endpoint.setCreatedAt(OffsetDateTime.now());
        endpoint.setUpdatedAt(OffsetDateTime.now());
        if (signingEnabled) {
            if (signingSecret == null || signingSecret.isBlank()) throw new IllegalArgumentException("Signing secret is required when signing is enabled");
            endpoint.setSigningEnabled(true);
            endpoint.setSigningSecretEncrypted(encryptionService.encrypt(signingSecret));
        }

        return toResponse(repository.save(endpoint));
    }

    public EndpointResponse registerEndpoint(String url, String description) { return registerEndpoint(url,null,description,false,null); }

    public List<EndpointResponse> getAllEndpoints() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public EndpointResponse getEndpointById(UUID id) {
        return repository.findById(id)
                .map(this::toResponse).orElseThrow(() -> new EndpointNotFoundException("Endpoint not found: " + id));
    }

    public EndpointResponse updateEndpointStatus(UUID id, boolean active) {
        WebhookEndpoint endpoint = repository.findById(id)
                .orElseThrow(() -> new EndpointNotFoundException(
                        "Endpoint not found: " + id));
        endpoint.setActive(active);
        endpoint.setUpdatedAt(OffsetDateTime.now());
        return toResponse(repository.save(endpoint));
    }

    private EndpointResponse toResponse(WebhookEndpoint endpoint) {
        return new EndpointResponse(endpoint.getId(), endpoint.getUrl(), endpoint.getName(), endpoint.getDescription(), endpoint.isActive(), endpoint.getCreatedAt(), endpoint.getUpdatedAt(), endpoint.isSigningEnabled());
    }

    private String fallbackName(URI uri) {
        String host = uri.getHost();
        return host != null && !host.isBlank() ? host : uri.toString().substring(0, Math.min(255, uri.toString().length()));
    }

    public record EndpointResponse(UUID id, String url, String name, String description, boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt, boolean signingEnabled) {}
}
