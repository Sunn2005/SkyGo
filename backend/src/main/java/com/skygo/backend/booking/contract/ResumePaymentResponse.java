package com.skygo.backend.booking.contract;

import java.time.Instant;

public record ResumePaymentResponse(String bookingId, String bookingStatus, String paymentStatus,
    String seatHoldId, Instant holdExpiresAt, long totalAmount, String currency) {}
