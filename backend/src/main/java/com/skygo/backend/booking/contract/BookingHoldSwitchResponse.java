package com.skygo.backend.booking.contract;

public record BookingHoldSwitchResponse(String switchId, PreviousBooking previousBooking, SeatHoldResponse seatHold) {
  public record PreviousBooking(String bookingId, String status) {}
}
