package com.skygo.backend.flight.service;

import com.skygo.backend.flight.contract.AirportPageResponse;
import com.skygo.backend.flight.contract.RegionResponse;
import com.skygo.backend.flight.exception.AirportQueryException;
import com.skygo.backend.flight.repository.AirportRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AirportService {
  private final AirportRepository repository;
  private final Clock clock;

  public AirportService(AirportRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public List<RegionResponse> regions() {
    return repository.regions();
  }

  public AirportPageResponse airports(String keyword, String country, String region,
      String origin, String pageValue, String sizeValue) {
    int page = integer("page", pageValue, 0, Integer.MAX_VALUE);
    int size = integer("size", sizeValue, 1, 100);
    if (keyword != null && keyword.length() > 200)
      throw new AirportQueryException("keyword", "Từ khóa tối đa 200 ký tự.");
    if (country != null && (!country.matches("[A-Z]{2}") || !repository.countryExists(country)))
      throw new AirportQueryException("countryCode", "Mã quốc gia không hợp lệ.");
    if (region != null && (!region.matches("[A-Z][A-Z0-9_]{0,31}") || !repository.regionExists(region)))
      throw new AirportQueryException("regionCode", "Mã khu vực không hợp lệ.");
    if (origin != null && (!origin.matches("[A-Z]{3}") || !repository.activeOriginExists(origin)))
      throw new AirportQueryException("origin", "Điểm đi phải là mã sân bay đang hoạt động.");
    return repository.airports(keyword == null ? null : keyword.strip(), country, region,
        origin, page, size, clock.instant());
  }

  private int integer(String field, String value, int min, int max) {
    try {
      int result = Integer.parseInt(value);
      if (result >= min && result <= max) return result;
    } catch (NumberFormatException ignored) {
      // Return the same contract error for invalid numbers and out-of-range values.
    }
    throw new AirportQueryException(field, field + " phải là số nguyên từ " + min + " đến " + max + ".");
  }
}
