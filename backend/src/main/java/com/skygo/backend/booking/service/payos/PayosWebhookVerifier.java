package com.skygo.backend.booking.service.payos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Component
public class PayosWebhookVerifier {
  private final String checksumKey;

  public PayosWebhookVerifier(@Value("${skygo.payos.checksum-key:}") String checksumKey) {
    this.checksumKey = checksumKey;
  }

  public record Transfer(long orderCode, String paymentLinkId, String reference,
      long amount, String currency, String transactionDateTime, boolean successful) {}

  public Transfer verify(JsonNode payload) {
    if (checksumKey.isBlank())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayOS is not configured");
    if (payload == null || !PayosHttpGateway.validResponseSignature(
        payload.path("data"), payload.path("signature").asText(), checksumKey))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PayOS signature");

    var data = payload.path("data");
    boolean success = payload.path("success").isBoolean() && payload.path("success").asBoolean()
        && "00".equals(payload.path("code").asText())
        && "00".equals(data.path("code").asText());
    if (!success) return new Transfer(0, "", "", 0, "", "", false);
    var order = data.path("orderCode");
    var amount = data.path("amount");
    String link = data.path("paymentLinkId").asText();
    String reference = data.path("reference").asText();
    String currency = data.path("currency").asText();
    String transactionDateTime = data.path("transactionDateTime").asText("");
    if (!order.isIntegralNumber() || !order.canConvertToLong() || order.asLong() <= 0
        || !amount.isIntegralNumber() || !amount.canConvertToLong() || amount.asLong() <= 0
        || !data.path("paymentLinkId").isTextual() || !data.path("reference").isTextual()
        || link.isBlank() || link.length() > 64 || reference.isBlank()
        || reference.length() > 128 || transactionDateTime.length() > 32
        || !currency.matches("[A-Z]{3}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incomplete PayOS transfer");
    return new Transfer(order.asLong(), link, reference, amount.asLong(),
        currency, transactionDateTime, true);
  }
}
