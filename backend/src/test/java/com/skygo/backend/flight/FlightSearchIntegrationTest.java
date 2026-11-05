package com.skygo.backend.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "SKYGO_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
    "spring.datasource.url=${SKYGO_TEST_DB_URL}",
    "spring.datasource.username=${SKYGO_TEST_DB_USERNAME:root}",
    "spring.datasource.password=${SKYGO_TEST_DB_PASSWORD:}",
    "skygo.seat-hold.cleanup-enabled=false"
})
@Transactional
class FlightSearchIntegrationTest {
  @Autowired WebApplicationContext context;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean Clock clock;
  MockMvc mvc;
  long outboundRoute;
  long returnRoute;
  long policy;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(Instant.parse("2027-01-10T00:00:00Z"));
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    airport("ZSA", "Asia/Ho_Chi_Minh");
    airport("ZSB", "Asia/Tokyo");
    outboundRoute = route("ZSA", "ZSB");
    returnRoute = route("ZSB", "ZSA");
    policy = jdbc.queryForObject("SELECT MIN(id) FROM pricing_policy", Long.class);
  }

  @Test
  void publicSearchFiltersFlightsAndCabinsWithoutReservingSeats() throws Exception {
    long valid = flight("search-valid", outboundRoute, "2027-01-10T01:00:00", "ON_SALE");
    fare(valid, "ECONOMY", 2, true, 105);
    fare(valid, "BUSINESS", 1, true, 1000); // Not enough seats for 2 adults.
    fare(flight("search-past", outboundRoute, "2027-01-10T00:00:00", "ON_SALE"), "ECONOMY", 10, true, 105);
    fare(flight("search-closed", outboundRoute, "2027-01-10T02:00:00", "CLOSED"), "ECONOMY", 10, true, 105);
    fare(flight("search-soldout", outboundRoute, "2027-01-10T03:00:00", "ON_SALE"), "ECONOMY", 1, true, 105);
    fare(flight("search-fareclosed", outboundRoute, "2027-01-10T04:00:00", "ON_SALE"), "ECONOMY", 10, false, 105);
    // Local Jan 11 midnight at ZSA is UTC Jan 10 17:00, excluded from Jan 10.
    fare(flight("search-nextday", outboundRoute, "2027-01-10T17:00:00", "ON_SALE"), "ECONOMY", 10, true, 105);
    mvc.perform(post("/api/v1/flights/search").contentType("application/json").content(request()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].flightId").value("search-valid"))
        .andExpect(jsonPath("$.data[0].origin.code").value("ZSA"))
        .andExpect(jsonPath("$.data[0].departureAt").value("2027-01-10T08:00:00+07:00"))
        .andExpect(jsonPath("$.data[0].arrivalAt").value("2027-01-10T12:00:00+09:00"))
        .andExpect(jsonPath("$.data[0].durationMinutes").value(120))
        .andExpect(jsonPath("$.data[0].fares.length()").value(1))
        .andExpect(jsonPath("$.data[0].fares[0].price.amount").value(116))
        .andExpect(jsonPath("$.data[0].fares[0].price.includesVat").value(true))
        .andExpect(jsonPath("$.data[0].fares[0].availableSeats").doesNotExist())
        .andExpect(jsonPath("$.pagination.totalElements").value(1));
    assertEquals(2, jdbc.queryForObject(
        "SELECT available_seats FROM flight_fare WHERE flight_id = ? AND cabin_class = 'ECONOMY'",
        Integer.class, valid));
    jdbc.update("UPDATE air_route SET is_active = 0 WHERE id = ?", outboundRoute);
    mvc.perform(post("/api/v1/flights/search").contentType("application/json").content(request()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void pagesFlightsRatherThanFaresAndUsesStableFlightIdOrdering() throws Exception {
    for (String id : new String[] {"search-b", "search-a"}) {
      long flight = flight(id, outboundRoute, "2027-01-10T02:00:00", "ON_SALE");
      fare(flight, "ECONOMY", 2, true, 105);
      fare(flight, "BUSINESS", 2, true, 1000);
    }
    String first = request().replace("\"size\":20", "\"size\":1");
    mvc.perform(post("/api/v1/flights/search").contentType("application/json").content(first))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].flightId").value("search-a"))
        .andExpect(jsonPath("$.data[0].fares.length()").value(2))
        .andExpect(jsonPath("$.pagination.totalElements").value(2))
        .andExpect(jsonPath("$.pagination.totalPages").value(2));
    mvc.perform(post("/api/v1/flights/search").contentType("application/json")
            .content(first.replace("\"page\":0", "\"page\":1")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].flightId").value("search-b"));
  }

  @Test
  void returnSearchUsesOriginalCriteriaAndReturnLocalDateBoundary() throws Exception {
    // Tokyo Jan 12 midnight is UTC Jan 11 15:00.
    fare(flight("search-return", returnRoute, "2027-01-11T15:00:00", "ON_SALE"), "BUSINESS", 2, true, 1000);
    fare(flight("search-tooearly", returnRoute, "2027-01-11T14:59:59", "ON_SALE"), "BUSINESS", 2, true, 1000);
    String returning = request().replace("ONE_WAY", "ROUND_TRIP").replace("OUTBOUND", "RETURN")
        .replace("\"returnDate\":null", "\"returnDate\":\"2027-01-12\"");
    mvc.perform(post("/api/v1/flights/search").contentType("application/json").content(returning))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].flightId").value("search-return"))
        .andExpect(jsonPath("$.data[0].origin.code").value("ZSB"))
        .andExpect(jsonPath("$.data[0].destination.code").value("ZSA"))
        .andExpect(jsonPath("$.data[0].departureAt").value("2027-01-12T00:00:00+09:00"));
  }

  @Test
  void invalidInputsAre422AndValidEmptySearchIs200() throws Exception {
    for (String invalid : new String[] {
        request().replace("\"adults\":2", "\"adults\":0"),
        request().replace("\"adults\":2", "\"adults\":1.5"),
        request().replace("\"children\":0", "\"children\":-1"),
        request().replace("\"infants\":3", "\"infants\":null"),
        request().replace("\"size\":20", "\"size\":101"),
        request().replace("\"page\":0", "\"page\":-1"),
        request().replace("\"ZSB\"", "\"ZSA\""),
        request().replace("\"ZSB\"", "\"XXX\""),
        request().replace("2027-01-10", "2027-02-30"),
        request().replace("2027-01-10", "2027-01-09"),
        request().replace("ONE_WAY", "INVALID"),
        request().replace("OUTBOUND", "RETURN"), "{}", "{"}) {
      mvc.perform(post("/api/v1/flights/search").contentType("application/json").content(invalid))
          .andExpect(status().is(422))
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    mvc.perform(post("/api/v1/flights/search").contentType("application/json").content(request()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.pagination.totalElements").value(0));
    jdbc.update("UPDATE airport SET is_active = 0 WHERE code = 'ZSB'");
    mvc.perform(post("/api/v1/flights/search").contentType("application/json").content(request()))
        .andExpect(status().is(422)).andExpect(jsonPath("$.error.details[0].field").value("destination"));
  }

  String request() {
    return """
        {"tripType":"ONE_WAY","origin":"ZSA","destination":"ZSB",
         "departureDate":"2027-01-10","returnDate":null,
         "passengerCounts":{"adults":2,"children":0,"infants":3},
         "direction":"OUTBOUND","page":0,"size":20}
        """;
  }

  void airport(String code, String zone) {
    jdbc.update("""
        INSERT INTO airport (code,name,city,country_code,country_name,region_code,
          region_name,region_sort_order,time_zone)
        VALUES (?, ?, ?, 'ZZ', 'Test', 'TEST_SEARCH', 'Test', 999, ?)
        """, code, code, code, zone);
  }

  long route(String from, String to) {
    jdbc.update("""
        INSERT INTO air_route (origin_airport_id,destination_airport_id,air_distance_km)
        SELECT o.id,d.id,100 FROM airport o JOIN airport d WHERE o.code = ? AND d.code = ?
        """, from, to);
    return jdbc.queryForObject("""
        SELECT r.id FROM air_route r JOIN airport o ON o.id=r.origin_airport_id
          JOIN airport d ON d.id=r.destination_airport_id WHERE o.code=? AND d.code=?
        """, Long.class, from, to);
  }

  long flight(String publicId, long route, String departure, String status) {
    var time = LocalDateTime.parse(departure);
    jdbc.update("""
        INSERT INTO flight (public_id,flight_number,route_id,departure_at_utc,arrival_at_utc,sale_status)
        VALUES (?, 'SGTEST', ?, ?, ?, ?)
        """, publicId, route, time, time.plusHours(2), status);
    return jdbc.queryForObject("SELECT id FROM flight WHERE public_id=?", Long.class, publicId);
  }

  void fare(long flight, String cabin, int seats, boolean onSale, long price) {
    jdbc.update("""
        INSERT INTO flight_fare (flight_id,pricing_policy_id,priced_distance_km,cabin_class,
          adult_base_fare,capacity,available_seats,is_on_sale) VALUES (?, ?, 100, ?, ?, 10, ?, ?)
        """, flight, policy, cabin, price, seats, onSale);
  }
}
