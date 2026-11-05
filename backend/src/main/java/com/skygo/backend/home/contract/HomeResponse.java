package com.skygo.backend.home.contract;

import java.time.OffsetDateTime;
import java.util.List;

public record HomeResponse(
    Period period,
    String routeSource,
    String routeSectionTitle,
    String secondarySectionTitle,
    UpcomingFlight upcomingFlight,
    RouteCard featuredRoute,
    List<RouteCard> otherRoutes) {
  public record Period(int year, int month) {}

  public record Airport(String code, String city) {}

  public record Price(long amount, String currency, boolean includesVat) {}

  public record RouteCard(
      long routeId,
      Airport origin,
      Airport destination,
      Price priceFrom,
      long monthlyPaidBookings,
      String imageUrl) {}

  public record UpcomingFlight(
      String bookingId,
      String bookingCode,
      String flightNumber,
      Airport origin,
      Airport destination,
      OffsetDateTime departureAt,
      OffsetDateTime arrivalAt,
      String status) {}
}
