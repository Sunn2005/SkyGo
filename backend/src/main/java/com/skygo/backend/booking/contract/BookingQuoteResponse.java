package com.skygo.backend.booking.contract;

import java.time.Instant;

public record BookingQuoteResponse(long totalAmount, String currency, Instant expiresAt) {}
