package com.relayforge.relayforge.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

@Component
public class WebhookDestinationValidator {

    private final AddressResolver addressResolver;
    private final boolean localTestReceiverEnabled;

    public WebhookDestinationValidator() {
        this(InetAddress::getAllByName, false);
    }

    public WebhookDestinationValidator(AddressResolver addressResolver) {
        this(addressResolver, false);
    }

    @Autowired
    public WebhookDestinationValidator(
            @Value("${relayforge.delivery.test-receiver.enabled:false}") boolean localTestReceiverEnabled) {
        this(InetAddress::getAllByName, localTestReceiverEnabled);
    }

    WebhookDestinationValidator(AddressResolver addressResolver, boolean localTestReceiverEnabled) {
        this.addressResolver = addressResolver;
        this.localTestReceiverEnabled = localTestReceiverEnabled;
    }

    public URI validate(String endpointUrl) throws UnknownHostException {
        URI uri;
        try {
            uri = URI.create(endpointUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Endpoint URL is invalid");
        }

        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Endpoint URL must be absolute HTTP or HTTPS without user information");
        }

        if (isLocalTestReceiverUrl(uri, scheme)) {
            return uri;
        }

        InetAddress[] addresses = addressResolver.resolve(uri.getHost());
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(this::isBlocked)) {
            throw new IllegalArgumentException("Endpoint host resolves to a prohibited address");
        }
        return uri;
    }

    private boolean isLocalTestReceiverUrl(URI uri, String scheme) {
        return localTestReceiverEnabled
                && "http".equalsIgnoreCase(scheme)
                && "localhost".equalsIgnoreCase(uri.getHost())
                && uri.getPort() == 9090
                && ("/accept".equals(uri.getPath()) || "/fail".equals(uri.getPath()))
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
    }

    private boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isBlockedIpv4(bytes);
        }
        if (address instanceof Inet6Address) {
            if ((bytes[0] & 0xfe) == 0xfc) { // Unique-local fc00::/7
                return true;
            }
            if (isIpv4Mapped(bytes)) {
                return isBlockedIpv4(Arrays.copyOfRange(bytes, 12, 16));
            }
        }
        return false;
    }

    private boolean isBlockedIpv4(byte[] address) {
        int first = address[0] & 0xff;
        int second = address[1] & 0xff;
        int third = address[2] & 0xff;
        return first == 0
                || first == 10
                || first == 127
                || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 198 && (second == 18 || second == 19 || second == 51 && third == 100))
                || (first == 203 && second == 0 && third == 113);
    }

    private boolean isIpv4Mapped(byte[] address) {
        for (int index = 0; index < 10; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        return address[10] == (byte) 0xff && address[11] == (byte) 0xff;
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
