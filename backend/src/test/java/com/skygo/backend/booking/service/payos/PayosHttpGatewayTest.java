package com.skygo.backend.booking.service.payos;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PayosHttpGatewayTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test void responseSignaturesHandleNestedContainersNullsAndScalars() throws Exception {
    var data = mapper.readTree("""
        {"z":"null","x":"undefined","amount":2200,"empty":{},
         "metadata":{"z":null,"a":[true,1,"x"]},"note":null,"transactions":[],"active":false}
        """);
    // Independently computed HMAC-SHA256 of the canonical provider payload.
    String signature = "98e52b0d8240ba8425afb2d57204bbd6685bf2b2de82cc409e06cabef0d28f5c";
    assertTrue(PayosHttpGateway.validResponseSignature(data, signature, "secret-1"));
    assertTrue(PayosHttpGateway.validResponseSignature(data,
        signature.toUpperCase(java.util.Locale.ROOT), "secret-1"));
    var tampered = mapper.readTree(data.toString().replace("[true,1,\"x\"]", "[false,1,\"x\"]"));
    assertFalse(PayosHttpGateway.validResponseSignature(tampered, signature, "secret-1"));
    assertFalse(PayosHttpGateway.validResponseSignature(data, signature, "wrong-test-key"));
    assertFalse(PayosHttpGateway.validResponseSignature(data, "invalid", "secret-1"));
    assertFalse(PayosHttpGateway.validResponseSignature(mapper.readTree("[]"), signature, "secret-1"));
  }

  @Test void lookupAndCancelVerifySignedTransactionArraysAndRejectTampering() throws Exception {
    var methods=new java.util.ArrayList<String>();
    var cancelBody=new AtomicReference<String>();
    var tamper=new java.util.concurrent.atomic.AtomicBoolean();
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/v2/payment-requests/123",exchange -> {
      methods.add(exchange.getRequestMethod()+" "+exchange.getRequestURI().getPath());
      cancelBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
      String json="""
          {"code":"00","data":{"id":"provider-123","orderCode":123,"amount":2200,
          "amountPaid":100,"status":"CANCELLED","transactions":[{"reference":"ref-1",
          "amount":100,"currency":"VND","transactionDateTime":"2030-01-10 07:15:00"}]},
          "signature":"a94943894246ebb1c10420ed4f34c43570584c1e5eab8e8efbed582d860b5f21"}
          """;
      byte[] bytes=(tamper.get()?json.replace("ref-1","ref-2"):json).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200,bytes.length);
      try(var stream=exchange.getResponseBody()) {stream.write(bytes);}
    });
    server.start();
    try {
      // Lookup/cancel require provider credentials, not frontend return URLs.
      var gateway=new PayosHttpGateway(mapper,"client","api","secret-1","","",
          "http://127.0.0.1:"+server.getAddress().getPort());
      var status=gateway.lookup(123);
      assertEquals(100,status.amountPaid());
      assertEquals("ref-1",status.transactions().getFirst().reference());
      assertEquals("CANCELLED",gateway.cancel(123).status());
      assertEquals("GET /v2/payment-requests/123",methods.get(0));
      assertEquals("POST /v2/payment-requests/123/cancel",methods.get(1));
      assertTrue(cancelBody.get().contains("cancellationReason"));
      tamper.set(true);
      assertThrows(PayosGateway.Failure.class,()->gateway.lookup(123));
    } finally {server.stop(0);}
  }

  @Test void sendsSignedBookingAmountAndReadsProviderQr() throws Exception {
    var body = new AtomicReference<String>();
    var clientHeader = new AtomicReference<String>();
    var keyHeader = new AtomicReference<String>();
    var tamper = new java.util.concurrent.atomic.AtomicBoolean();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v2/payment-requests", exchange -> {
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      clientHeader.set(exchange.getRequestHeaders().getFirst("x-client-id"));
      keyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
      String json = """
          {"code":"00","data":{"paymentLinkId":"provider-123","orderCode":123,
          "amount":2200,"currency":"VND","status":"PENDING",
          "description":"SKYGO 123","qrCode":"VIETQR-DATA",
          "checkoutUrl":"https://pay.payos.vn/web/provider-123"},
          "signature":"fe296212cee368c97c20a88dfc2fa3df1e3909d6b12d1692efc582f84cb47250"}
          """;
      byte[] response = (tamper.get() ? json.replace("VIETQR-DATA", "BAD-QR") : json)
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      try (var stream = exchange.getResponseBody()) { stream.write(response); }
    });
    server.start();
    try {
      var gateway = new PayosHttpGateway(mapper, "client-1", "api-1", "secret-1",
          "https://example.test/return", "https://example.test/cancel",
          "http://127.0.0.1:" + server.getAddress().getPort());
      var link = gateway.create(123, 2200, "SKYGO 123",
          Instant.parse("2030-01-10T00:15:00Z"));
      assertEquals("client-1", clientHeader.get());
      assertEquals("api-1", keyHeader.get());
      var sent = mapper.readTree(body.get());
      assertEquals(123, sent.path("orderCode").asLong());
      assertEquals(2200, sent.path("amount").asLong());
      assertEquals("SKYGO 123", sent.path("description").asText());
      assertEquals("https://example.test/return", sent.path("returnUrl").asText());
      assertEquals("https://example.test/cancel", sent.path("cancelUrl").asText());
      assertEquals(Instant.parse("2030-01-10T00:15:00Z").getEpochSecond(),
          sent.path("expiredAt").asLong());
      assertEquals("24af64cded74788e521b462e251ac7cba7c0496f0ef888117bcc3e4b050edd1c",
          sent.path("signature").asText());
      assertEquals("VIETQR-DATA", link.qrCode());
      assertEquals("provider-123", link.paymentLinkId());
      tamper.set(true);
      assertFalse(assertThrows(PayosGateway.Failure.class,
          () -> gateway.create(123, 2200, "SKYGO 123",
              Instant.parse("2030-01-10T00:15:00Z"))).definitive());
    } finally { server.stop(0); }
  }

  @Test void distinguishesRejectedRequestFromUncertainServerFailure() throws Exception {
    var status = new java.util.concurrent.atomic.AtomicInteger(400);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v2/payment-requests", exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.sendResponseHeaders(status.get(), -1);
      exchange.close();
    });
    server.start();
    try {
      var gateway = new PayosHttpGateway(mapper, "client", "api", "checksum",
          "https://example.test/return", "https://example.test/cancel",
          "http://127.0.0.1:" + server.getAddress().getPort());
      var expiry = Instant.parse("2030-01-10T00:15:00Z");
      assertTrue(assertThrows(PayosGateway.Failure.class,
          () -> gateway.create(123, 2200, "SKYGO 123", expiry)).definitive());
      status.set(500);
      assertFalse(assertThrows(PayosGateway.Failure.class,
          () -> gateway.create(123, 2200, "SKYGO 123", expiry)).definitive());
    } finally { server.stop(0); }
  }
}
