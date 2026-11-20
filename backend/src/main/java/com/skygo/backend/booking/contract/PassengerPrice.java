package com.skygo.backend.booking.contract;

// Per-person amounts exclude VAT; tax is rounded once for the whole booking.
public record PassengerPrice(long fareSubtotal, long extraBaggageAmount, long subtotal) {
  public static PassengerPrice of(long fare, long baggage) {
    return new PassengerPrice(fare, baggage, Math.addExact(fare, baggage));
  }
}
