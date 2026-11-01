package com.relayforge.relayforge.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebhookDestinationValidatorTests {

    @Test
    void blocksLoopbackAddresses() throws Exception {
        WebhookDestinationValidator validator = validator("127.0.0.1");

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://receiver.example.test/hook"));
    }

    @Test
    void blocksPrivateAndLinkLocalAddresses() throws Exception {
        for (String address : Arrays.asList("10.1.2.3", "172.20.0.1", "192.168.1.2", "169.254.1.5")) {
            WebhookDestinationValidator validator = validator(address);
            assertThrows(IllegalArgumentException.class,
                    () -> validator.validate("https://receiver.example.test/hook"), address);
        }
    }

    @Test
    void blocksAHostIfAnyResolvedAddressIsProhibited() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("8.8.8.8");
        InetAddress privateAddress = InetAddress.getByName("192.168.1.5");
        WebhookDestinationValidator validator = new WebhookDestinationValidator(
                host -> new InetAddress[]{publicAddress, privateAddress});

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("https://receiver.example.test/hook"));
    }

    @Test
    void rejectsUserInformationAndUnsupportedSchemes() throws Exception {
        WebhookDestinationValidator validator = validator("8.8.8.8");

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("https://user:secret@receiver.example.test/hook"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("ftp://receiver.example.test/hook"));
    }

    @Test
    void allowsAnAbsoluteHttpUrlWithPublicResolvedAddress() throws Exception {
        WebhookDestinationValidator validator = validator("8.8.8.8");

        assertEquals(URI.create("https://receiver.example.test/hook"),
                validator.validate("https://receiver.example.test/hook"));
    }

    @Test
    void allowsOnlyTheLocalTestReceiverWhenExplicitlyEnabled() throws Exception {
        WebhookDestinationValidator validator = new WebhookDestinationValidator(
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")}, true);

        assertEquals(URI.create("http://localhost:9090/accept"),
                validator.validate("http://localhost:9090/accept"));
        assertEquals(URI.create("http://localhost:9090/fail"),
                validator.validate("http://localhost:9090/fail"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://localhost:9090/admin"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://127.0.0.1:9090/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://localhost:9090/fail?anything=true"));
    }

    @Test
    void allowsComposeReceiverAndPublishedLocalReceiverStatusPaths() throws Exception {
        WebhookDestinationValidator validator = new WebhookDestinationValidator(
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")}, true);

        assertEquals(URI.create("http://receiver:9090/status/429"),
                validator.validate("http://receiver:9090/status/429"));
        assertEquals(URI.create("http://localhost:9091/status/503"),
                validator.validate("http://localhost:9091/status/503"));
    }

    @Test
    void localTestReceiverAllowlistRejectsOtherHostsPortsSchemesAndPaths() throws Exception {
        WebhookDestinationValidator validator = new WebhookDestinationValidator(
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")}, true);

        for (String url : Arrays.asList(
                "http://receiver:8080/accept",
                "https://receiver:9090/accept",
                "http://localhost:9091/admin",
                "http://127.0.0.1:9091/accept",
                "http://receiver:9090/status/600",
                "http://receiver:9090/accept#fragment")) {
            assertThrows(IllegalArgumentException.class, () -> validator.validate(url), url);
        }
    }

    @Test
    void localTestReceiverRemainsBlockedByDefault() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new WebhookDestinationValidator(host -> new InetAddress[]{
                        InetAddress.getByName("127.0.0.1")})
                        .validate("http://localhost:9090/fail"));
    }

    private WebhookDestinationValidator validator(String address) {
        return new WebhookDestinationValidator(
                host -> new InetAddress[]{InetAddress.getByName(address)});
    }
}
