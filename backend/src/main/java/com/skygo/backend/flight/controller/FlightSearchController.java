package com.skygo.backend.flight.controller;

import com.skygo.backend.flight.contract.FlightSearchRequest;
import com.skygo.backend.flight.contract.FlightSearchResponse;
import com.skygo.backend.flight.contract.NearbyFlightSearchRequest;
import com.skygo.backend.flight.contract.NearbyFlightSearchResponse;
import com.skygo.backend.auth.contract.DataResponse;
import com.skygo.backend.flight.service.FlightSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FlightSearchController {
  private final FlightSearchService service;

  public FlightSearchController(FlightSearchService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/flights/search")
  public FlightSearchResponse search(@Valid @RequestBody FlightSearchRequest request) {
    return service.search(request);
  }

  @PostMapping("/api/v1/flights/search/nearby-days")
  public DataResponse<NearbyFlightSearchResponse> nearbyDays(
      @Valid @RequestBody NearbyFlightSearchRequest request) {
    return new DataResponse<>(service.nearbyDays(request));
  }
}
