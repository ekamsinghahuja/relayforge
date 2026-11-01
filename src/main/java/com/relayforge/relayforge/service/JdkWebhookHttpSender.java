package com.relayforge.relayforge.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class JdkWebhookHttpSender implements WebhookHttpSender {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public int postJson(URI uri, String payload) throws Exception {
        return send(uri,payload,null,null,null);
    }

    @Override
    public int postJson(URI uri, String payload, UUID eventId, UUID deliveryId, String secret) throws Exception {
        return send(uri,payload,eventId,deliveryId,secret);
    }

    private int send(URI uri, String payload, UUID eventId, UUID deliveryId, String secret) throws Exception {
        HttpRequest.Builder builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json");
        if(eventId!=null) builder.header("X-RelayForge-Event",eventId.toString());
        if(deliveryId!=null) builder.header("X-RelayForge-Delivery",deliveryId.toString());
        if(secret!=null) {
            String timestamp=Long.toString(Instant.now().getEpochSecond());
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
            String signature=HexFormat.of().formatHex(mac.doFinal((timestamp+"."+payload).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            builder.header("X-RelayForge-Timestamp",timestamp).header("X-RelayForge-Signature","sha256="+signature);
        }
        HttpRequest request=builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
