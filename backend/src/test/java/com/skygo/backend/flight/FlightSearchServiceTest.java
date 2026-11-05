package com.skygo.backend.flight;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.skygo.backend.flight.contract.FlightSearchRequest;
import com.skygo.backend.flight.contract.FlightSearchRequest.*;
import com.skygo.backend.flight.exception.FlightSearchException;
import com.skygo.backend.flight.repository.FlightSearchRepository;
import com.skygo.backend.flight.repository.FlightSearchRepository.*;
import com.skygo.backend.flight.service.FlightSearchService;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FlightSearchServiceTest {
  final FlightSearchRepository repository = mock(FlightSearchRepository.class);
  final Instant now = Instant.parse("2027-03-14T01:00:00Z");
  final FlightSearchService service = new FlightSearchService(
      repository, Clock.fixed(now, ZoneOffset.UTC), "SG", "SkyGo");

  @BeforeEach
  void setup() {
    when(repository.activeAirport("AAA")).thenReturn(Optional.of(
        new AirportRow(1, "AAA", "New York", "America/New_York")));
    when(repository.activeAirport("BBB")).thenReturn(Optional.of(
        new AirportRow(2, "BBB", "Tokyo", "Asia/Tokyo")));
    when(repository.search(any())).thenReturn(new SearchRows(0, List.of()));
  }

  @Test
  void departureDayUsesOriginTimezoneAndHandlesDaylightSavingTime() {
    service.search(request(TripType.ONE_WAY, Direction.OUTBOUND, "2027-03-14", null));
    var criteria = ArgumentCaptor.forClass(Criteria.class);
    verify(repository).search(criteria.capture());
    assertEquals(Instant.parse("2027-03-14T05:00:00Z"), criteria.getValue().dayStart());
    assertEquals(Instant.parse("2027-03-15T04:00:00Z"), criteria.getValue().dayEnd());
    assertEquals(3, criteria.getValue().requiredSeats()); // 2 adults + 1 child, not 4 infants.
    assertEquals(20, criteria.getValue().size());
  }

  @Test
  void returnReversesAirportsAndUsesReturnOriginsLocalDay() {
    service.search(request(TripType.ROUND_TRIP, Direction.RETURN, "2027-03-14", "2027-03-16"));
    var criteria = ArgumentCaptor.forClass(Criteria.class);
    verify(repository).search(criteria.capture());
    assertEquals(2, criteria.getValue().originId());
    assertEquals(1, criteria.getValue().destinationId());
    assertEquals(Instant.parse("2027-03-15T15:00:00Z"), criteria.getValue().dayStart());
    assertEquals(Instant.parse("2027-03-16T15:00:00Z"), criteria.getValue().dayEnd());
  }

  @Test
  void groupsCabinsAndRoundsAdultVatHalfUpWithoutMultiplyingByPassengerCounts() {
    var departure = Instant.parse("2027-03-14T10:00:00Z");
    when(repository.search(any())).thenReturn(new SearchRows(1, List.of(
        new FareRow("f1", "SG1", departure, departure.plusSeconds(7200), "ECONOMY",
            new BigDecimal("105"), new BigDecimal("0.10")),
        new FareRow("f1", "SG1", departure, departure.plusSeconds(7200), "BUSINESS",
            new BigDecimal("1000"), new BigDecimal("0.10")))));
    var response = service.search(request(TripType.ONE_WAY, Direction.OUTBOUND, "2027-03-14", null));
    assertEquals(1, response.data().size());
    var flight = response.data().getFirst();
    assertEquals(2, flight.fares().size());
    assertEquals(116, flight.fares().getFirst().price().amount());
    assertEquals(1100, flight.fares().getLast().price().amount());
    assertTrue(flight.fares().getFirst().price().includesVat());
    assertEquals(120, flight.durationMinutes());
    assertEquals(ZoneOffset.ofHours(-4), flight.departureAt().getOffset());
    assertEquals(ZoneOffset.ofHours(9), flight.arrivalAt().getOffset());
  }

  @Test
  void validatesDatesUsingLocalTodayAndRejectsInvalidTripCombinations() {
    // At now it is still March 13 in New York, even though UTC is March 14.
    assertDoesNotThrow(() -> service.search(request(TripType.ONE_WAY, Direction.OUTBOUND, "2027-03-13", null)));
    assertThrows(FlightSearchException.class, () -> service.search(
        request(TripType.ONE_WAY, Direction.OUTBOUND, "2027-03-12", null)));
    assertThrows(FlightSearchException.class, () -> service.search(
        request(TripType.ROUND_TRIP, Direction.RETURN, "2027-03-14", "2027-03-14")));
    assertThrows(FlightSearchException.class, () -> service.search(
        request(TripType.ROUND_TRIP, Direction.OUTBOUND, "2027-03-14", null)));
    assertThrows(FlightSearchException.class, () -> service.search(
        request(TripType.ONE_WAY, Direction.RETURN, "2027-03-14", null)));
    assertThrows(FlightSearchException.class, () -> service.search(
        request(TripType.ONE_WAY, Direction.OUTBOUND, "2027-03-14", "2027-03-16")));
  }

  FlightSearchRequest request(TripType type, Direction direction, String departure, String returning) {
    return new FlightSearchRequest(type, "AAA", "BBB", LocalDate.parse(departure),
        returning == null ? null : LocalDate.parse(returning), new PassengerCounts(2, 1, 4),
        direction, null, null);
  }
}
