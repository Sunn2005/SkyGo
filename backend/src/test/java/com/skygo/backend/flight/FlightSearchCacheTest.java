package com.skygo.backend.flight;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.skygo.backend.flight.contract.FlightSearchResponse;
import com.skygo.backend.flight.contract.FlightSearchResponse.*;
import com.skygo.backend.flight.contract.FlightSearchRequest.Direction;
import com.skygo.backend.flight.contract.FlightSearchRequest.TripType;
import com.skygo.backend.flight.contract.NearbyFlightSearchResponse;
import com.skygo.backend.flight.repository.FlightSearchRepository.DayWindow;
import com.skygo.backend.flight.service.FlightSearchCache;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class FlightSearchCacheTest {
  final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  @SuppressWarnings("unchecked")
  final ValueOperations<String, String> values = mock(ValueOperations.class);
  final ObjectMapper mapper = new ObjectMapper();
  final FlightSearchCache cache = new FlightSearchCache(redis, mapper, Duration.ofMinutes(1));
  final FlightSearchCache.Query query = new FlightSearchCache.Query(1, 2,
      Instant.parse("2027-03-14T05:00:00Z"), Instant.parse("2027-03-15T04:00:00Z"), 2, 0, 20);

  @Test
  void missWritesOneMinuteEntryAndHitSkipsLoader() {
    when(redis.opsForValue()).thenReturn(values);
    var response = new FlightSearchResponse(List.of(new Flight("f1", "SG1",
        new Airline("SG", "SkyGo"), new Airport("AAA", "New York"),
        new Airport("BBB", "Tokyo"), OffsetDateTime.parse("2027-03-14T06:00:00-04:00"),
        OffsetDateTime.parse("2027-03-15T00:00:00+09:00"), 300,
        List.of(new Fare("ECONOMY", new Price(1000, "VND", true))))),
        new Pagination(0, 20, 1, 1));
    String json = mapper.writeValueAsString(response);
    when(values.get(anyString())).thenReturn(null, json);
    @SuppressWarnings("unchecked")
    Supplier<FlightSearchResponse> loader = mock(Supplier.class);
    when(loader.get()).thenReturn(response);

    assertEquals(response, cache.getOrLoad(query, loader));
    assertEquals(response, cache.getOrLoad(query, loader));

    verify(loader).get();
    var key = ArgumentCaptor.forClass(String.class);
    verify(values).set(key.capture(), eq(json), eq(Duration.ofMinutes(1)));
    assertTrue(key.getValue().startsWith("skygo:flights:search:v1:"));
    verify(values, times(2)).get(key.getValue());
  }

  @Test
  void redisReadFailureFallsBackToLoader() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("offline"));
    var response = new FlightSearchResponse(List.of(), new Pagination(0, 20, 0, 0));

    assertEquals(response, cache.getOrLoad(query, () -> response));
    verify(values).set(anyString(), anyString(), eq(Duration.ofMinutes(1)));
  }

  @Test
  void nearbyCacheUsesSeparateKeyAndPreservesFlightOffsets() {
    when(redis.opsForValue()).thenReturn(values);
    var day = LocalDate.parse("2027-03-16");
    var flight = new Flight("f1", "SG1", new Airline("SG", "SkyGo"),
        new Airport("AAA", "New York"), new Airport("BBB", "Tokyo"),
        OffsetDateTime.parse("2027-03-16T06:00:00-04:00"),
        OffsetDateTime.parse("2027-03-17T00:00:00+09:00"), 300,
        List.of(new Fare("ECONOMY", new Price(1000, "VND", true))));
    var response = new NearbyFlightSearchResponse(day, day.minusDays(3), day.plusDays(3),
        Direction.OUTBOUND, List.of(new NearbyFlightSearchResponse.Day(day, true, null,
            List.of(flight), new NearbyFlightSearchResponse.Pagination(0, 10, 1, 1, false))));
    String json = mapper.writeValueAsString(response);
    when(values.get(anyString())).thenReturn(null, json);
    var nearbyQuery = new FlightSearchCache.NearbyQuery(1, 2, day, null,
        TripType.ONE_WAY, Direction.OUTBOUND, day,
        List.of(new DayWindow(day, Instant.parse("2027-03-16T04:00:00Z"),
            Instant.parse("2027-03-17T04:00:00Z"))), 2);
    @SuppressWarnings("unchecked")
    Supplier<NearbyFlightSearchResponse> loader = mock(Supplier.class);
    when(loader.get()).thenReturn(response);

    assertEquals(response, cache.getNearbyOrLoad(nearbyQuery, loader));
    assertEquals(response, cache.getNearbyOrLoad(nearbyQuery, loader));

    verify(loader).get();
    var key = ArgumentCaptor.forClass(String.class);
    verify(values).set(key.capture(), eq(json), eq(Duration.ofMinutes(1)));
    assertTrue(key.getValue().startsWith("skygo:flights:nearby:v1:"));
    verify(values, times(2)).get(key.getValue());
  }
}
