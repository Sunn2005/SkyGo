package com.skygo.backend.booking.service.payos;

import com.skygo.backend.booking.exception.BookingException;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

@Component
public class PayosHttpGateway implements PayosGateway {
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper mapper;
  private final String clientId, apiKey, checksumKey, returnUrl, cancelUrl, baseUrl;

  public PayosHttpGateway(ObjectMapper mapper,
      @Value("${skygo.payos.client-id:}") String clientId,
      @Value("${skygo.payos.api-key:}") String apiKey,
      @Value("${skygo.payos.checksum-key:}") String checksumKey,
      @Value("${skygo.payos.return-url:}") String returnUrl,
      @Value("${skygo.payos.cancel-url:}") String cancelUrl,
      @Value("${skygo.payos.base-url:https://api-merchant.payos.vn}") String baseUrl) {
    this.mapper = mapper;
    this.clientId = clientId;
    this.apiKey = apiKey;
    this.checksumKey = checksumKey;
    this.returnUrl = returnUrl;
    this.cancelUrl = cancelUrl;
    this.baseUrl = baseUrl;
  }

  @Override public void requireConfigured() {
    if (clientId.isBlank() || apiKey.isBlank() || checksumKey.isBlank()
        || !absoluteUri(returnUrl) || !absoluteUri(cancelUrl))
      throw new BookingException(503, "PAYOS_NOT_CONFIGURED",
          "PayOS chưa được cấu hình đầy đủ trên backend.");
  }

  @Override public Link create(long orderCode, long amount, String description,
      Instant expiresAt) {
    requireConfigured();
    if (expiresAt.getEpochSecond() > Integer.MAX_VALUE)
      throw new BookingException(503, "PAYOS_EXPIRY_UNSUPPORTED",
          "Thời hạn thanh toán không được PayOS hỗ trợ.");
    String signature = sign(amount, cancelUrl, description, orderCode, returnUrl,
        checksumKey);
    var payload = Map.<String, Object>of("orderCode", orderCode, "amount", amount,
        "description", description, "cancelUrl", cancelUrl, "returnUrl", returnUrl,
        "expiredAt", expiresAt.getEpochSecond(), "signature", signature);
    try {
      var request = HttpRequest.newBuilder(URI.create(baseUrl + "/v2/payment-requests"))
          .timeout(Duration.ofSeconds(12))
          .header("Content-Type", "application/json")
          .header("x-client-id", clientId).header("x-api-key", apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
          .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 500) throw new Failure(false, null);
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new Failure(true, null);
      JsonNode root = mapper.readTree(response.body());
      if (!"00".equals(root.path("code").asText())) throw new Failure(true, null);
      JsonNode data = root.path("data");
      if (!validResponseSignature(data, root.path("signature").asText(), checksumKey))
        throw new Failure(false, null);
      var link = new Link(data.path("paymentLinkId").asText(),
          data.path("orderCode").asLong(), data.path("amount").asLong(),
          data.path("currency").asText(), data.path("status").asText(),
          data.path("description").asText(), data.path("qrCode").asText(),
          data.path("checkoutUrl").asText());
      if (link.paymentLinkId().isBlank() || link.qrCode().isBlank()
          || !absoluteUri(link.checkoutUrl()) || !"PENDING".equals(link.status())
          || !"VND".equals(link.currency()) || link.orderCode() != orderCode
          || link.amount() != amount || !description.equals(link.description()))
        throw new Failure(false, null);
      return link;
    } catch (IOException ex) {
      throw new Failure(false, ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new Failure(false, ex);
    } catch (Failure ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new Failure(false, ex);
    }
  }

  static String sign(long amount, String cancelUrl, String description, long orderCode,
      String returnUrl, String key) {
    String input = "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description="
        + description + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;
    return hmac(input, key);
  }

  static boolean validResponseSignature(JsonNode data, String signature, String key) {
    if (!data.isObject() || signature == null || !signature.matches("[a-fA-F0-9]{64}"))
      return false;
    var sorted = new TreeMap<String, String>();
    for (var field : data.properties()) {
      JsonNode value = field.getValue();
      if (!value.isValueNode()) return false;
      sorted.put(field.getKey(), value.isNull() ? "" : value.asText());
    }
    String joined = sorted.entrySet().stream()
        .map(field -> field.getKey() + "=" + field.getValue())
        .collect(java.util.stream.Collectors.joining("&"));
    return MessageDigest.isEqual(hmac(joined, key).getBytes(StandardCharsets.US_ASCII),
        signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
  }

  private static String hmac(String input, String key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
    }
  }

  private static boolean absoluteUri(String value) {
    try {
      URI uri = URI.create(value);
      return uri.isAbsolute() && uri.getScheme() != null && !uri.getScheme().isBlank();
    } catch (IllegalArgumentException ex) { return false; }
  }
}
