package com.relayforge.relayforge.service;

import java.net.URI;
import java.util.UUID;

public interface WebhookHttpSender {

    int postJson(URI uri, String payload) throws Exception;

    default int postJson(URI uri, String payload, UUID eventId, UUID deliveryId, String secret) throws Exception {
        return postJson(uri, payload);
    }
}
