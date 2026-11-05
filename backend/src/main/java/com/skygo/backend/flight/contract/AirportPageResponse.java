package com.skygo.backend.flight.contract;

import java.util.List;

public record AirportPageResponse(List<AirportResponse> data, Pagination pagination) {
  public record Pagination(int page, int size, long totalElements, long totalPages) {}
}
