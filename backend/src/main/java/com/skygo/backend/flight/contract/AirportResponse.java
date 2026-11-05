package com.skygo.backend.flight.contract;

public record AirportResponse(
    String code, String name, String city, String countryName, String regionCode) {}
