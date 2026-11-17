package com.skygo.backend.flight.service;

import com.skygo.backend.flight.contract.FlightSearchRequest;
import com.skygo.backend.flight.contract.FlightSearchRequest.Direction;
import com.skygo.backend.flight.contract.FlightSearchRequest.TripType;
import com.skygo.backend.flight.contract.FlightSearchResponse;
import com.skygo.backend.flight.contract.FlightSearchResponse.*;
import com.skygo.backend.flight.contract.NearbyFlightSearchRequest;
import com.skygo.backend.flight.contract.NearbyFlightSearchResponse;
import com.skygo.backend.flight.contract.NearbyFlightSearchResponse.DisabledReason;
import com.skygo.backend.flight.exception.FlightSearchException;
import com.skygo.backend.flight.repository.FlightSearchRepository;
import com.skygo.backend.flight.repository.FlightSearchRepository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.skygo.backend.booking.service.SeatHoldExpiryService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FlightSearchService {
  private final FlightSearchRepository repository;
  private final Clock clock;
  private final Airline airline;
  private final SeatHoldExpiryService expiry;
  private final FlightSearchCache cache;
  private final TransactionTemplate readTransaction;

  public FlightSearchService(FlightSearchRepository repository, Clock clock,
      @Value("${skygo.airline.code}") String airlineCode,
      @Value("${skygo.airline.name}") String airlineName,
      SeatHoldExpiryService expiry, PlatformTransactionManager manager, FlightSearchCache cache) {
    this.repository = repository;
    this.clock = clock;
    this.airline = new Airline(airlineCode, airlineName);
    this.expiry = expiry;
    this.cache = cache;
    readTransaction = new TransactionTemplate(manager);
    readTransaction.setReadOnly(true);
    readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  public FlightSearchResponse search(FlightSearchRequest request) {
    var context = resolve(request);
    var from = context.from();
    var to = context.to();
    var date = context.date();
    var fromZone = ZoneId.of(from.timeZone());
    int page = request.page() == null ? 0 : request.page();
    int size = request.size() == null ? 20 : request.size();
    long requiredSeats = (long) request.passengerCounts().adults() + request.passengerCounts().children();
    var dayStart = date.atStartOfDay(fromZone).toInstant();
    var dayEnd = date.plusDays(1).atStartOfDay(fromZone).toInstant();
    expiry.expireDue();
    var key = new FlightSearchCache.Query(from.id(), to.id(), dayStart, dayEnd, requiredSeats, page, size);
    return cache.getOrLoad(key, () -> {
      var rows = readTransaction.execute(status -> repository.search(new Criteria(from.id(), to.id(),
          dayStart, dayEnd, clock.instant(), requiredSeats, page, size)));
      return new FlightSearchResponse(flights(rows.fares(), from, to),
          new Pagination(page, size, rows.total(), (rows.total() + size - 1) / size));
    });
  }

  public NearbyFlightSearchResponse nearbyDays(NearbyFlightSearchRequest request) {
    var criteria = request.toSearchRequest();
    var context = resolve(criteria);
    var center = context.date();
    final LocalDate start;
    final LocalDate end;
    try {
      start = center.minusDays(NearbyFlightSearchRequest.DAYS_EACH_SIDE);
      end = center.plusDays(NearbyFlightSearchRequest.DAYS_EACH_SIDE);
      // MariaDB DATETIME cannot represent the exclusive upper bound beyond year 9999.
      if (start.getYear() < 1000 || end.plusDays(1).getYear() > 9999)
        throw new DateTimeException("Date range outside database bounds");
    } catch (DateTimeException ex) {
      throw new FlightSearchException(request.direction() == Direction.RETURN ? "returnDate" : "departureDate",
          "Khoảng ngày tìm kiếm không được hỗ trợ.");
    }
    var zone = ZoneId.of(context.from().timeZone());
    var dates = start.datesUntil(end.plusDays(1)).toList();
    expiry.expireDue();
    var checkedAt = clock.instant();
    var today = checkedAt.atZone(zone).toLocalDate();
    var windows = dates.stream().filter(day -> disabledReason(day, criteria, today) == null)
        .map(day -> new DayWindow(day, day.atStartOfDay(zone).toInstant(),
            day.plusDays(1).atStartOfDay(zone).toInstant())).toList();
    long requiredSeats = (long) request.passengerCounts().adults() + request.passengerCounts().children();
    var key = new FlightSearchCache.NearbyQuery(context.from().id(), context.to().id(),
        request.departureDate(), request.returnDate(), request.tripType(), request.direction(),
        today, windows, requiredSeats);
    return cache.getNearbyOrLoad(key, () -> {
      var rows = readTransaction.execute(status -> repository.searchNearby(new NearbyCriteria(
          context.from().id(), context.to().id(), windows, checkedAt, requiredSeats)));
      var days = dates.stream().map(day -> {
        var reason = disabledReason(day, criteria, today);
        var found = rows.getOrDefault(day, new SearchRows(0, List.of()));
        int size = NearbyFlightSearchRequest.PAGE_SIZE;
        return new NearbyFlightSearchResponse.Day(day, reason == null, reason,
            flights(found.fares(), context.from(), context.to()),
            new NearbyFlightSearchResponse.Pagination(0, size, found.total(),
                (found.total() + size - 1) / size, found.total() > size));
      }).toList();
      return new NearbyFlightSearchResponse(center, start, end, request.direction(), days);
    });
  }

  private DisabledReason disabledReason(LocalDate day, FlightSearchRequest request, LocalDate today) {
    if (day.isBefore(today)) return DisabledReason.PAST_DATE;
    if (request.tripType() == TripType.ROUND_TRIP) {
      if (request.direction() == Direction.RETURN && !day.isAfter(request.departureDate()))
        return DisabledReason.RETURN_NOT_AFTER_DEPARTURE;
      if (request.direction() == Direction.OUTBOUND && !day.isBefore(request.returnDate()))
        return DisabledReason.DEPARTURE_NOT_BEFORE_RETURN;
    }
    return null;
  }

  private record SearchContext(AirportRow from, AirportRow to, LocalDate date) {}

  private SearchContext resolve(FlightSearchRequest request) {
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
    return new SearchContext(from, to, returning ? request.returnDate() : request.departureDate());
  }

  private List<Flight> flights(List<FareRow> rows, AirportRow from, AirportRow to) {
    var fromZone = ZoneId.of(from.timeZone());
    var toZone = ZoneId.of(to.timeZone());
    var grouped = new LinkedHashMap<String, List<FareRow>>();
    for (var row : rows) grouped.computeIfAbsent(row.flightId(), key -> new ArrayList<>()).add(row);
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
    return flights;
  }

  private AirportRow airport(String code, String field) {
    return repository.activeAirport(code).orElseThrow(
        () -> new FlightSearchException(field, "Sân bay không tồn tại hoặc ngừng hoạt động."));
  }
}
