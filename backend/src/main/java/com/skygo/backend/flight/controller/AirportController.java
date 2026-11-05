package com.skygo.backend.flight.controller;

import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.flight.contract.AirportPageResponse;
import com.skygo.backend.flight.contract.RegionResponse;
import com.skygo.backend.flight.service.AirportService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AirportController {
  private final AirportService service;

  public AirportController(AirportService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/regions")
  public DataResponse<List<RegionResponse>> regions() {
    return new DataResponse<>(service.regions());
  }

  @GetMapping("/api/v1/airports")
  public AirportPageResponse airports(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String countryCode,
      @RequestParam(required = false) String regionCode,
      @RequestParam(required = false) String origin,
      @RequestParam(defaultValue = "0") String page,
      @RequestParam(defaultValue = "20") String size) {
    return service.airports(keyword, countryCode, regionCode, origin, page, size);
  }
}
