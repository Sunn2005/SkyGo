package com.skygo.backend.home.service;

import com.skygo.backend.home.contract.HomeResponse;
import com.skygo.backend.home.repository.HomeRepository;
import com.skygo.backend.home.repository.HomeRepository.RouteRow;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
  private static final ZoneId VIETNAM_TIME = ZoneId.of("Asia/Ho_Chi_Minh");
  private final HomeRepository repository;
  private final Clock clock;

  public HomeService(HomeRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public HomeResponse home(Long userId) {
    var now = clock.instant();
    var month = YearMonth.now(clock.withZone(VIETNAM_TIME));
    var routes =
        repository.availableRoutes(
            month.atDay(1).atStartOfDay(VIETNAM_TIME).toInstant(),
            month.plusMonths(1).atDay(1).atStartOfDay(VIETNAM_TIME).toInstant(),
            now);
    long popularCount = routes.stream().filter(route -> route.monthlyPaidBookings() > 0).count();
    String source = routes.isEmpty() ? "EMPTY" : popularCount >= 3 ? "POPULAR" : "SUGGESTED";
    var visible = routes.stream().limit(3).map(this::card).toList();
    var upcoming =
        userId == null
            ? null
            : repository.nextConfirmedFlight(userId, now).map(this::upcoming).orElse(null);
    return new HomeResponse(
        new HomeResponse.Period(month.getYear(), month.getMonthValue()),
        source,
        source.equals("POPULAR") ? "Tuyến bay được chọn nhiều" : "Gợi ý tuyến bay",
        source.equals("POPULAR") ? "Cũng được yêu thích" : "Có thể bạn cũng thích",
        upcoming,
        visible.isEmpty() ? null : visible.getFirst(),
        visible.size() <= 1 ? List.of() : visible.subList(1, visible.size()));
  }

  private HomeResponse.RouteCard card(RouteRow row) {
    return new HomeResponse.RouteCard(
        row.routeId(),
        new HomeResponse.Airport(row.originCode(), row.originCity()),
        new HomeResponse.Airport(row.destinationCode(), row.destinationCity()),
        new HomeResponse.Price(row.priceFromVnd(), "VND", true),
        row.monthlyPaidBookings(),
        row.destinationCoverImagePath());
  }

  private HomeResponse.UpcomingFlight upcoming(HomeRepository.UpcomingRow row) {
    return new HomeResponse.UpcomingFlight(
        row.bookingId(),
        row.bookingCode(),
        row.flightNumber(),
        new HomeResponse.Airport(row.originCode(), row.originCity()),
        new HomeResponse.Airport(row.destinationCode(), row.destinationCity()),
        row.departureAtUtc().atZone(ZoneId.of(row.originTimeZone())).toOffsetDateTime(),
        row.arrivalAtUtc().atZone(ZoneId.of(row.destinationTimeZone())).toOffsetDateTime(),
        "CONFIRMED");
  }
}
