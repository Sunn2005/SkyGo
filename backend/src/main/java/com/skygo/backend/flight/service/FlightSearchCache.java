package com.skygo.backend.flight.service;

import com.skygo.backend.flight.contract.FlightSearchResponse;
import com.skygo.backend.flight.contract.FlightSearchRequest.Direction;
import com.skygo.backend.flight.contract.FlightSearchRequest.TripType;
import com.skygo.backend.flight.contract.NearbyFlightSearchResponse;
import com.skygo.backend.flight.repository.FlightSearchRepository.DayWindow;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.DateTimeFeature;

@Component
public class FlightSearchCache {
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final ObjectReader reader;
  private final ObjectReader nearbyReader;
  private final Duration ttl;

  public FlightSearchCache(StringRedisTemplate redis, ObjectMapper mapper,
      @Value("${skygo.cache.flight-search.ttl:1m}") Duration ttl) {
    this.redis = redis;
    this.mapper = mapper;
    this.reader = mapper.readerFor(FlightSearchResponse.class)
        .without(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    this.nearbyReader = mapper.readerFor(NearbyFlightSearchResponse.class)
        .without(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    this.ttl = ttl;
  }

  public record Query(long originId, long destinationId, Instant dayStart, Instant dayEnd,
      long requiredSeats, int page, int size) {}

  public FlightSearchResponse getOrLoad(Query query, Supplier<FlightSearchResponse> loader) {
    return load("skygo:flights:search:v1:", query, reader, loader);
  }

  public record NearbyQuery(long originId, long destinationId, LocalDate departureDate,
      LocalDate returnDate, TripType tripType, Direction direction, LocalDate today,
      List<DayWindow> windows, long requiredSeats) {}

  public NearbyFlightSearchResponse getNearbyOrLoad(NearbyQuery query,
      Supplier<NearbyFlightSearchResponse> loader) {
    return load("skygo:flights:nearby:v1:", query, nearbyReader, loader);
  }

  private <T> T load(String prefix, Object query, ObjectReader responseReader, Supplier<T> loader) {
    // Do not cache uncommitted data from a surrounding write transaction.
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && !TransactionSynchronizationManager.isCurrentTransactionReadOnly())
      return loader.get();

    String key = key(prefix, query);
    try {
      String json = redis.opsForValue().get(key);
      if (json != null) return responseReader.readValue(json);
    } catch (DataAccessException | JacksonException ex) {
      // Redis failures or malformed entries fall back to the database.
    }

    T result = loader.get();
    try {
      redis.opsForValue().set(key, mapper.writeValueAsString(result), ttl);
    } catch (DataAccessException | JacksonException ex) {
      // A failed cache write must not hide a successful search.
    }
    return result;
  }

  private String key(String prefix, Object query) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(query));
      return prefix + HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
