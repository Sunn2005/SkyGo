package com.skygo.backend.booking.controller;

import com.skygo.backend.booking.service.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public class PayosWebhookController {
  private final PaymentWebhookService service;
  public PayosWebhookController(PaymentWebhookService service) { this.service = service; }

  @PostMapping("/api/v1/payos/webhook")
  public ResponseEntity<Void> receive(@RequestBody JsonNode payload) {
    service.receive(payload);
    return ResponseEntity.ok().build();
  }
}
