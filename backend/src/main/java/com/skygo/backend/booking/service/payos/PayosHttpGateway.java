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
    requireCredentials();
    if (!absoluteUri(returnUrl) || !absoluteUri(cancelUrl))
      throw new BookingException(503, "PAYOS_NOT_CONFIGURED",
          "PayOS chưa được cấu hình đầy đủ trên backend.");
  }

  private void requireCredentials() {
    if (clientId.isBlank() || apiKey.isBlank() || checksumKey.isBlank())
      throw new BookingException(503,"PAYOS_NOT_CONFIGURED","PayOS chưa được cấu hình đầy đủ trên backend.");
  }

  @Override public Status lookup(long orderCode) { return readStatus(orderCode, false); }
  @Override public Status cancel(long orderCode) { return readStatus(orderCode, true); }

  private Status readStatus(long orderCode, boolean cancel) {
    requireCredentials();
    try {
      var builder = HttpRequest.newBuilder(URI.create(baseUrl + "/v2/payment-requests/"
          + orderCode + (cancel ? "/cancel" : ""))).timeout(Duration.ofSeconds(12))
          .header("x-client-id",clientId).header("x-api-key",apiKey)
          .header("User-Agent","SkyGoBackend/1.0").header("Accept","application/json");
      if (cancel) builder.header("Content-Type","application/json").POST(
          HttpRequest.BodyPublishers.ofString("{\"cancellationReason\":\"Seat hold released or expired\"}"));
      else builder.GET();
      var response = http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
      if (response.statusCode()!=200) throw new Failure(false,null);
      var root = mapper.readTree(response.body());
      var data = root.path("data");
      if (!"00".equals(root.path("code").asText())
          || !validResponseSignature(data,root.path("signature").asText(),checksumKey))
        throw new Failure(false,null);
      for (String field : List.of("orderCode","amount","amountPaid")) {
        var value=data.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong()<0)
          throw new Failure(false,null);
      }
      String id=data.path("id").asText(), status=data.path("status").asText();
      if (id.isBlank() || id.length()>64 || data.path("orderCode").asLong()!=orderCode
          || data.path("amount").asLong()<=0
          || !Set.of("PENDING","PROCESSING","PAID","CANCELLED","EXPIRED").contains(status))
        throw new Failure(false,null);
      var transfers=new ArrayList<Transfer>();
      var transactions=data.path("transactions");
      if (!transactions.isArray() && !(transactions.isObject() && transactions.isEmpty()))
        throw new Failure(false,null);
      for (int i=0;i<transactions.size();i++) {
        var item=transactions.get(i);
        var amount=item.path("amount");
        String reference=item.path("reference").asText();
        String currency=item.path("currency").asText("VND");
        String time=item.path("transactionDateTime").asText("");
        if (!item.path("reference").isTextual() || reference.isBlank() || reference.length()>128
            || !amount.isIntegralNumber() || !amount.canConvertToLong() || amount.asLong()<=0
            || !currency.matches("[A-Z]{3}") || time.length()>32)
          throw new Failure(false,null);
        transfers.add(new Transfer(reference,amount.asLong(),currency,time));
      }
      return new Status(id,orderCode,data.path("amount").asLong(),
          data.path("amountPaid").asLong(),status,List.copyOf(transfers));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt(); throw new Failure(false,ex);
    } catch (Failure ex) { throw ex;
    } catch (IOException | RuntimeException ex) { throw new Failure(false,ex); }
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
          .header("User-Agent", "SkyGoBackend/1.0")
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
      final String text;
      if (value.isNull()) {
        text = "";
      } else if (value.isObject() || value.isArray()) {
        // Jackson 3 cannot coerce containers to text. Canonicalize before scalar conversion.
        text = canonicalJson(value);
      } else {
        String scalar = value.asText();
        text = value.isTextual() && (scalar.equals("null") || scalar.equals("undefined"))
            ? "" : scalar;
      }
      sorted.put(field.getKey(), text);
    }
    String joined = sorted.entrySet().stream()
        .map(field -> field.getKey() + "=" + field.getValue())
        .collect(java.util.stream.Collectors.joining("&"));
    return MessageDigest.isEqual(hmac(joined, key).getBytes(StandardCharsets.US_ASCII),
        signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
  }

  private static String canonicalJson(JsonNode node) {
    if (node.isObject()) {
      var sorted=new TreeMap<String,JsonNode>();
      for(var field:node.properties()) sorted.put(field.getKey(),field.getValue());
      var mapper=new ObjectMapper();
      return "{"+sorted.entrySet().stream().map(e -> mapper.writeValueAsString(e.getKey())
          +":"+canonicalJson(e.getValue())).collect(java.util.stream.Collectors.joining(","))+"}";
    }
    if (node.isArray()) {
      var entries=new ArrayList<String>();
      for(int i=0;i<node.size();i++) entries.add(canonicalJson(node.get(i)));
      return "["+String.join(",",entries)+"]";
    }
    return node.toString();
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
