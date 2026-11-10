package com.skygo.backend.flight.contract;

import java.time.Instant;
import java.util.List;

public record FavoriteFlightResponse(List<Item> data,
    FlightSearchResponse.Pagination pagination, List<String> favoriteFlightIds) {
  public record Item(Instant savedAt, String availability, FlightSearchResponse.Flight flight) {}
}
