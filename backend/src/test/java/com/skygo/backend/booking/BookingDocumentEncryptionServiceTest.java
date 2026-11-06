package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;

import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.service.BookingDocumentEncryptionService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BookingDocumentEncryptionServiceTest {
  private static final String KEY="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

  @Test
  void protectsDocumentNumberAndBindsItToBookingAndPassenger() {
    var service=new BookingDocumentEncryptionService(KEY,"test-v1");
    byte[] first=service.encrypt("000012345678","booking-a","passenger-a");
    byte[] second=service.encrypt("000012345678","booking-a","passenger-a");
    assertFalse(Arrays.equals(first,second));
    assertFalse(new String(first,StandardCharsets.UTF_8).contains("000012345678"));
    assertEquals("000012345678",service.decrypt(first,"booking-a","passenger-a"));
    assertEquals("BOOKING_DOCUMENT_INVALID",assertThrows(BookingException.class,
        ()->service.decrypt(first,"booking-b","passenger-a")).code);
    assertEquals("BOOKING_DOCUMENT_INVALID",assertThrows(BookingException.class,
        ()->service.decrypt(first,"booking-a","passenger-b")).code);
    first[first.length-1]^=1;
    assertEquals("BOOKING_DOCUMENT_INVALID",assertThrows(BookingException.class,
        ()->service.decrypt(first,"booking-a","passenger-a")).code);
  }

  @Test
  void refusesMissingKey() {
    var service=new BookingDocumentEncryptionService("","v1");
    assertEquals("DOCUMENT_ENCRYPTION_UNAVAILABLE",assertThrows(BookingException.class,
        ()->service.encrypt("12345","booking","passenger")).code);
  }
}
