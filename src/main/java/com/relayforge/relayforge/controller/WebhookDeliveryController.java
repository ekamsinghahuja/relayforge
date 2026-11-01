package com.relayforge.relayforge.controller;

import com.relayforge.relayforge.service.WebhookDeliveryApiService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
public class WebhookDeliveryController {
    private final WebhookDeliveryApiService service;
    public WebhookDeliveryController(WebhookDeliveryApiService service) { this.service=service; }
    @GetMapping public Page<WebhookDeliveryApiService.DeliveryView> list(@RequestParam(required=false) String status,
            @RequestParam(required=false) UUID eventId,@RequestParam(required=false) UUID endpointId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.list(status,eventId,endpointId,page,size); }
    @GetMapping("/{id}") public WebhookDeliveryApiService.DeliveryView get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/attempts") public java.util.List<WebhookDeliveryApiService.AttemptView> attempts(@PathVariable UUID id) { return service.attempts(id); }
    @PostMapping("/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED) public void retry(@PathVariable UUID id) { service.retry(id); }
}
