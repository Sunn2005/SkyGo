package com.skygo.backend.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "SKYGO_TEST_DB_URL", matches = ".+")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=${SKYGO_TEST_DB_URL}",
      "spring.datasource.username=${SKYGO_TEST_DB_USERNAME:root}",
      "spring.datasource.password=${SKYGO_TEST_DB_PASSWORD:}"
    })
class AuthIntegrationTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  @Autowired ObjectMapper mapper;
  HttpClient client = HttpClient.newHttpClient();

  String body(String phone, String password) {
    return "{\"phoneNumber\":\"" + phone + "\",\"password\":\"" + password + "\"}";
  }

  String registration(String phone) {
    return "{\"fullName\":\"Test User\",\"email\":\"test@example.com\",\"confirmPassword\":\"SkyGoDemo@2027\","
        + body(phone, "SkyGoDemo@2027").substring(1);
  }

  HttpResponse<String> call(String method, String path, String body, String token)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/" + path))
            .header("Content-Type", "application/json");
    if (token != null) request.header("Authorization", "Bearer " + token);
    return client.send(
        request
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void migrationAndAuthLifecycle() throws Exception {
    assertEquals(0, flyway.migrate().migrationsExecuted);
    assertEquals(
        25,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",
            Integer.class));
    String phone = "09" + String.format("%08d", new Random().nextInt(100000000));
    var registered = call("POST", "register", registration(phone), null);
    assertEquals(201, registered.statusCode());
    assertEquals("Test User", mapper.readTree(registered.body()).at("/data/fullName").asString());
    assertEquals(
        "test@example.com", mapper.readTree(registered.body()).at("/data/email").asString());
    for (String invalidEmail : List.of("", "invalid-email")) {
      var invalid =
          call(
              "POST",
              "register",
              registration(phone).replace("test@example.com", invalidEmail),
              null);
      assertEquals(422, invalid.statusCode());
      assertEquals(
          "email", mapper.readTree(invalid.body()).at("/error/details/0/field").asString());
    }
    assertEquals(
        422,
        call(
                "POST",
                "register",
                registration(phone).replace("\"email\":\"test@example.com\",", ""),
                null)
            .statusCode());
    assertEquals(
        409, call("POST", "register", registration("+84" + phone.substring(1)), null).statusCode());
    assertEquals(
        422,
        call("POST", "register", registration(phone).replace("SkyGoDemo@2027", "short"), null)
            .statusCode());
    assertEquals(422, call("POST", "login", body("bad", "wrong"), null).statusCode());
    assertEquals(401, call("GET", "me", null, null).statusCode());
    for (int i = 0; i < 4; i++)
      assertEquals(401, call("POST", "login", body(phone, "wrong"), null).statusCode());
    var locked = call("POST", "login", body(phone, "wrong"), null);
    assertEquals(429, locked.statusCode());
    assertTrue(locked.headers().firstValue("Retry-After").isPresent());
    assertEquals(
        5,
        jdbc.queryForObject(
            "SELECT failure_count FROM login_guard WHERE phone_normalized=?",
            Integer.class,
            "+84" + phone.substring(1)));
    assertEquals(429, call("POST", "login", body(phone, "SkyGoDemo@2027"), null).statusCode());
    jdbc.update(
        "UPDATE login_guard SET"
            + " failure_at_1_utc=NULL,failure_at_2_utc=NULL,failure_at_3_utc=NULL,failure_at_4_utc=NULL,failure_at_5_utc=NULL,failure_count=0,locked_until_utc=NULL"
            + " WHERE phone_normalized=?",
        "+84" + phone.substring(1));
    var login = call("POST", "login", body(phone, "SkyGoDemo@2027"), null);
    assertEquals(200, login.statusCode(), login.body());
    String token = mapper.readTree(login.body()).at("/data/accessToken").asString();
    assertEquals(200, call("GET", "me", null, token).statusCode());
    String token2 =
        mapper
            .readTree(call("POST", "login", body(phone, "SkyGoDemo@2027"), null).body())
            .at("/data/accessToken")
            .asString();
    assertEquals(204, call("POST", "logout", null, token).statusCode());
    assertEquals(401, call("GET", "me", null, token).statusCode());
    assertEquals(200, call("GET", "me", null, token2).statusCode());
    jdbc.update(
        "UPDATE auth_session SET expires_at_utc=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE"
            + " user_id=(SELECT id FROM app_user WHERE phone_normalized=?)",
        "+84" + phone.substring(1));
    assertEquals(401, call("GET", "me", null, token2).statusCode());
  }

  @Test
  void concurrentRegistrationAndFailures() throws Exception {
    String phone = "08" + String.format("%08d", new Random().nextInt(100000000));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<Integer>> tasks =
          List.of(
              () -> call("POST", "register", registration(phone), null).statusCode(),
              () -> call("POST", "register", registration(phone), null).statusCode());
      var statuses = new ArrayList<Integer>();
      for (var f : executor.invokeAll(tasks)) statuses.add(f.get());
      Collections.sort(statuses);
      assertEquals(List.of(201, 409), statuses);
      var failures = new ArrayList<Callable<Integer>>();
      for (int i = 0; i < 5; i++)
        failures.add(() -> call("POST", "login", body(phone, "wrong"), null).statusCode());
      statuses.clear();
      for (var f : executor.invokeAll(failures)) statuses.add(f.get());
      Collections.sort(statuses);
      assertEquals(List.of(401, 401, 401, 401, 429), statuses);
    }
  }
}
