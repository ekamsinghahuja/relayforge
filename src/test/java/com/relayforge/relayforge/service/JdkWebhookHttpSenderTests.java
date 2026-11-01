package com.relayforge.relayforge.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdkWebhookHttpSenderTests {

    @Test
    void postsOriginalJsonAndDoesNotFollowRedirects() throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicInteger redirectedRequestCount = new AtomicInteger();
        server.createContext("/accept", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/private");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/private", exchange -> {
            redirectedRequestCount.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            JdkWebhookHttpSender sender = new JdkWebhookHttpSender();
            int accepted = sender.postJson(uri(server, "/accept"), "{\"hello\":\"world\"}");
            int redirected = sender.postJson(uri(server, "/redirect"), "{}");

            assertEquals(201, accepted);
            assertEquals("{\"hello\":\"world\"}", requestBody.get());
            assertEquals("application/json", contentType.get());
            assertEquals(302, redirected);
            assertEquals(0, redirectedRequestCount.get());
        } finally {
            server.stop(0);
        }
    }

    private URI uri(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
