package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
class FavoriteFlightIntegrationTest extends BookingFixture {
  @AfterEach
  void removeFavorites() {
    for(long owner:users)jdbc.update("DELETE FROM favorite_flight WHERE user_id=?",owner);
  }

  @Test
  void idempotentSaveDeleteAndOwnershipNeverHoldSeats() throws Exception {
    String path="/api/v1/favorite-flights/"+outFlight;
    mvc.perform(put(path)).andExpect(status().isUnauthorized());
    for(int i=0;i<2;i++)mvc.perform(put(path).with(as(user)))
        .andExpect(status().isNoContent());
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM favorite_flight WHERE user_id=?",Integer.class,user));
    assertEquals(4,seats(outFare));
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold WHERE user_id=?",Integer.class,user));
    mvc.perform(delete(path).with(as(otherUser))).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/favorite-flights").with(as(otherUser)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.favoriteFlightIds.length()").value(0));
    mvc.perform(get("/api/v1/favorite-flights").with(as(user)))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.data[0].flight.flightId").value(outFlight))
        .andExpect(jsonPath("$.data[0].availability").value("AVAILABLE"))
        .andExpect(jsonPath("$.data[0].flight.fares.length()").value(2));
    for(int i=0;i<2;i++)mvc.perform(delete(path).with(as(user))).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/favorite-flights").with(as(user)))
        .andExpect(jsonPath("$.pagination.totalElements").value(0));
  }

  @Test
  void paginationCurrentPriceAndUnavailableFlightsRemainVisible() throws Exception {
    mvc.perform(put("/api/v1/favorite-flights/"+outFlight).with(as(user))).andExpect(status().isNoContent());
    mvc.perform(put("/api/v1/favorite-flights/"+backFlight).with(as(user))).andExpect(status().isNoContent());
    jdbc.update("UPDATE flight_fare SET adult_base_fare=2000 WHERE id=?",outFare);
    long expected=jdbc.queryForObject("SELECT ROUND(2000*(1+p.vat_rate)) FROM pricing_policy p JOIN flight_fare f ON f.pricing_policy_id=p.id WHERE f.id=?",Long.class,outFare);
    jdbc.update("UPDATE favorite_flight SET created_at_utc='2000-01-01' WHERE flight_id=(SELECT id FROM flight WHERE public_id=?)",backFlight);
    mvc.perform(get("/api/v1/favorite-flights?size=1").with(as(user)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.totalElements").value(2))
        .andExpect(jsonPath("$.pagination.totalPages").value(2))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.favoriteFlightIds.length()").value(2))
        .andExpect(jsonPath("$.data[0].flight.fares[?(@.cabinClass=='ECONOMY')].price.amount").value(org.hamcrest.Matchers.contains((int)expected)));
    jdbc.update("UPDATE flight_fare SET available_seats=0 WHERE id IN (?,?)",outFare,businessFare);
    mvc.perform(get("/api/v1/favorite-flights?size=1").with(as(user)))
        .andExpect(jsonPath("$.data[0].availability").value("SOLD_OUT"))
        .andExpect(jsonPath("$.data[0].flight.fares.length()").value(0));
    jdbc.update("UPDATE flight SET sale_status='CLOSED' WHERE public_id=?",outFlight);
    mvc.perform(get("/api/v1/favorite-flights?size=1").with(as(user)))
        .andExpect(jsonPath("$.data[0].availability").value("CLOSED"));
    org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(90000));
    mvc.perform(get("/api/v1/favorite-flights?size=1").with(as(user)))
        .andExpect(jsonPath("$.data[0].availability").value("DEPARTED"));
    mvc.perform(put("/api/v1/favorite-flights/"+outFlight).with(as(user))).andExpect(status().isNoContent());
    mvc.perform(put("/api/v1/favorite-flights/"+outFlight).with(as(otherUser))).andExpect(status().is(422));
  }

  @Test
  void validatesInputAndSupportsWebDeletePreflight() throws Exception {
    mvc.perform(put("/api/v1/favorite-flights/missing").with(as(user)))
        .andExpect(status().isNotFound());
    for(String query:new String[]{"page=-1","page=abc","size=101","size=0"})
      mvc.perform(get("/api/v1/favorite-flights?"+query).with(as(user)))
          .andExpect(status().is(422));
    mvc.perform(options("/api/v1/favorite-flights/"+outFlight)
        .header("Origin","http://localhost:8081")
        .header("Access-Control-Request-Method","DELETE"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Methods",org.hamcrest.Matchers.containsString("DELETE")));
  }
}
