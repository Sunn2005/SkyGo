package com.skygo.backend.home;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.skygo.backend.home.repository.HomeRepository;
import com.skygo.backend.home.repository.HomeRepository.RouteRow;
import com.skygo.backend.home.repository.HomeRepository.UpcomingRow;
import com.skygo.backend.home.service.HomeService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HomeServiceTest {
  private final HomeRepository repository = mock(HomeRepository.class);

  @Test
  void noFlightsReturnsEmptyHomeAndUsesVietnamMonthBoundary() {
    var now = Instant.parse("2026-10-31T17:30:00Z");
    when(repository.availableRoutes(any(), any(), any())).thenReturn(List.of());

    var result = new HomeService(repository, Clock.fixed(now, ZoneOffset.UTC)).home(null);

    assertEquals(2026, result.period().year());
    assertEquals(11, result.period().month());
    assertEquals("EMPTY", result.routeSource());
    assertNull(result.featuredRoute());
    assertNull(result.upcomingFlight());
    assertTrue(result.otherRoutes().isEmpty());
    verify(repository)
        .availableRoutes(
            Instant.parse("2026-10-31T17:00:00Z"),
            Instant.parse("2026-11-30T17:00:00Z"),
            now);
    verify(repository, never()).nextConfirmedFlight(anyLong(), any());
  }

  @Test
  void popularRoutesAndNextPaidFlightUseSnapshotTimeZones() {
    var now = Instant.parse("2026-11-05T07:00:00Z");
    when(repository.availableRoutes(any(), any(), any()))
        .thenReturn(
            List.of(
                route(1, "SGN", "Sài Gòn", "HAN", "Hà Nội", 3_564_000, 3),
                route(2, "HAN", "Hà Nội", "DAD", "Đà Nẵng", 1_991_000, 2),
                route(3, "SGN", "Sài Gòn", "PQC", "Phú Quốc", 1_133_000, 1)));
    when(repository.nextConfirmedFlight(eq(7L), any()))
        .thenReturn(
            Optional.of(
                new UpcomingRow(
                    "booking-public-id",
                    "BOOK123",
                    "SG1001",
                    "SGN",
                    "Sài Gòn",
                    "Asia/Ho_Chi_Minh",
                    "HAN",
                    "Hà Nội",
                    "Asia/Ho_Chi_Minh",
                    Instant.parse("2026-11-16T03:30:00Z"),
                    Instant.parse("2026-11-16T05:35:00Z"))));

    var result = new HomeService(repository, Clock.fixed(now, ZoneOffset.UTC)).home(7L);

    assertEquals("POPULAR", result.routeSource());
    assertEquals("Tuyến bay được chọn nhiều", result.routeSectionTitle());
    assertEquals("SGN", result.featuredRoute().origin().code());
    assertEquals(3, result.featuredRoute().monthlyPaidBookings());
    assertEquals(3_564_000, result.featuredRoute().priceFrom().amount());
    assertEquals("/airports/HAN.jpg", result.featuredRoute().imageUrl());
    assertTrue(result.featuredRoute().priceFrom().includesVat());
    assertEquals(2, result.otherRoutes().size());
    assertEquals("CONFIRMED", result.upcomingFlight().status());
    assertEquals("2026-11-16T10:30+07:00", result.upcomingFlight().departureAt().toString());
    assertEquals("2026-11-16T12:35+07:00", result.upcomingFlight().arrivalAt().toString());
  }

  @Test
  void sparsePaidHistoryUsesSuggestionLabels() {
    when(repository.availableRoutes(any(), any(), any()))
        .thenReturn(
            List.of(
                route(1, "SGN", "Sài Gòn", "HAN", "Hà Nội", 3_564_000, 1),
                route(2, "HAN", "Hà Nội", "DAD", "Đà Nẵng", 1_991_000, 0)));

    var result =
        new HomeService(
                repository,
                Clock.fixed(Instant.parse("2026-11-05T07:00:00Z"), ZoneOffset.UTC))
            .home(null);

    assertEquals("SUGGESTED", result.routeSource());
    assertEquals("Gợi ý tuyến bay", result.routeSectionTitle());
    assertEquals("Có thể bạn cũng thích", result.secondarySectionTitle());
    assertEquals(1, result.otherRoutes().size());
  }

  private RouteRow route(
      long id,
      String origin,
      String originCity,
      String destination,
      String destinationCity,
      long price,
      long paidCount) {
    return new RouteRow(
        id, origin, originCity, destination, destinationCity,
        "/airports/" + destination + ".jpg", price, paidCount);
  }
}
