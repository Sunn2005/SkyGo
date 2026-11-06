package com.skygo.backend.booking.service.payos;

import java.time.Instant;

public interface PayosGateway {
  void requireConfigured();
  Link create(long orderCode, long amount, String description, Instant expiresAt);

  record Link(String paymentLinkId, long orderCode, long amount, String currency,
      String status, String description, String qrCode, String checkoutUrl) {}

  class Failure extends RuntimeException {
    private final boolean definitive;
    public Failure(boolean definitive, Throwable cause) {
      super("PayOS payment link request failed", cause);
      this.definitive = definitive;
    }
    public boolean definitive() { return definitive; }
  }
}
