package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.service.BookingBaggagePricingService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BookingBaggagePricingServiceTest {
  @Test
  void taxesNetTicketsAndBaggageTogetherOnce() {
    var price=BookingBaggagePricingService.price(1000000,650000,new BigDecimal("0.10"));
    assertEquals(165000,price.vatAmount());
    assertEquals(1815000,price.totalAmount());
    assertEquals(650000,price.extraBaggageAmount());
  }
  @Test
  void roundsAggregateVatHalfUpAndHandlesZeroExtras() {
    assertEquals(1,BookingBaggagePricingService.price(5,0,new BigDecimal("0.1")).vatAmount());
    assertEquals(0,BookingBaggagePricingService.price(4,0,new BigDecimal("0.1")).vatAmount());
    assertEquals(561000,BookingBaggagePricingService.price(510000,0,new BigDecimal("0.1")).totalAmount());
  }
  @Test
  void rejectsNegativeAmountsUnsupportedTaxAndOverflowInsteadOfWrapping() {
    assertThrows(BookingException.class,()->BookingBaggagePricingService.price(-1,0,new BigDecimal("0.1")));
    assertThrows(BookingException.class,()->BookingBaggagePricingService.price(1,-1,new BigDecimal("0.1")));
    assertThrows(BookingException.class,()->BookingBaggagePricingService.price(1,0,new BigDecimal("0.2")));
    assertThrows(BookingException.class,()->BookingBaggagePricingService.price(Long.MAX_VALUE,1,new BigDecimal("0.1")));
    assertThrows(BookingException.class,()->BookingBaggagePricingService.price(Long.MAX_VALUE,0,new BigDecimal("0.1")));
  }
}
