package com.skygo.backend.flight.service;

import com.skygo.backend.flight.contract.FlightSearchRequest;
import com.skygo.backend.flight.contract.FlightSearchRequest.Direction;
import com.skygo.backend.flight.contract.FlightSearchRequest.TripType;
import com.skygo.backend.flight.contract.FlightSearchResponse;
import com.skygo.backend.flight.contract.FlightSearchResponse.*;
import com.skygo.backend.flight.exception.FlightSearchException;
import com.skygo.backend.flight.repository.FlightSearchRepository;
import com.skygo.backend.flight.repository.FlightSearchRepository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FlightSearchService {
  private final FlightSearchRepository repository;
  private final Clock clock;
  private final Airline airline;

  public FlightSearchService(FlightSearchRepository repository, Clock clock,
      @Value("${skygo.airline.code}") String airlineCode,
      @Value("${skygo.airline.name}") String airlineName) {
    this.repository = repository;
    this.clock = clock;
    this.airline = new Airline(airlineCode, airlineName);
  }

  public FlightSearchResponse search(FlightSearchRequest request) {
    if (request.origin().equals(request.destination()))
      throw new FlightSearchException("destination", "Điểm đến phải khác điểm đi.");
    var origin = airport(request.origin(), "origin");
    var destination = airport(request.destination(), "destination");
    var now = clock.instant();
    if (request.departureDate().isBefore(now.atZone(ZoneId.of(origin.timeZone())).toLocalDate()))
      throw new FlightSearchException("departureDate", "Ngày đi không được ở trong quá khứ tại sân bay đi.");
    if (request.tripType() == TripType.ROUND_TRIP) {
      if (request.returnDate() == null || !request.returnDate().isAfter(request.departureDate()))
        throw new FlightSearchException("returnDate", "Ngày về phải sau ngày đi.");
    } else {
      if (request.returnDate() != null)
        throw new FlightSearchException("returnDate", "Hành trình một chiều không có ngày về.");
      if (request.direction() == Direction.RETURN)
        throw new FlightSearchException("direction", "Chiều về chỉ áp dụng cho hành trình khứ hồi.");
    }

    boolean returning = request.direction() == Direction.RETURN;
    var from = returning ? destination : origin;
    var to = returning ? origin : destination;
    var date = returning ? request.returnDate() : request.departureDate();
    var fromZone = ZoneId.of(from.timeZone());
    var toZone = ZoneId.of(to.timeZone());
    int page = request.page() == null ? 0 : request.page();
    int size = request.size() == null ? 20 : request.size();
    var rows = repository.search(new Criteria(from.id(), to.id(),
        date.atStartOfDay(fromZone).toInstant(), date.plusDays(1).atStartOfDay(fromZone).toInstant(),
        now, (long) request.passengerCounts().adults() + request.passengerCounts().children(), page, size));
    var grouped = new LinkedHashMap<String, List<FareRow>>();
    for (var row : rows.fares()) grouped.computeIfAbsent(row.flightId(), key -> new ArrayList<>()).add(row);
    var flights = new ArrayList<Flight>();
    for (var group : grouped.values()) {
      var first = group.getFirst();
      var fares = group.stream().map(row -> new Fare(row.cabinClass(), new Price(
          row.baseFare().multiply(BigDecimal.ONE.add(row.vatRate()))
              .setScale(0, RoundingMode.HALF_UP).longValueExact(), "VND", true))).toList();
      flights.add(new Flight(first.flightId(), first.flightNumber(), airline,
          new Airport(from.code(), from.city()), new Airport(to.code(), to.city()),
          first.departure().atZone(fromZone).toOffsetDateTime(),
          first.arrival().atZone(toZone).toOffsetDateTime(),
          Duration.between(first.departure(), first.arrival()).toMinutes(), fares));
    }
    return new FlightSearchResponse(flights,
        new Pagination(page, size, rows.total(), (rows.total() + size - 1) / size));
  }

  private AirportRow airport(String code, String field) {
    return repository.activeAirport(code).orElseThrow(
        () -> new FlightSearchException(field, "Sân bay không tồn tại hoặc ngừng hoạt động."));
  }
}
