package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.service.ReviewEncryptionService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReviewEncryptionServiceTest {
  static final String KEY=Base64.getEncoder().encodeToString(new byte[32]);
  final ReviewEncryptionService crypto=new ReviewEncryptionService(KEY,"test-v1");
  final ReviewEncryptionService.Context context=new ReviewEncryptionService.Context("r1",1,2,3,1);
  @Test
  void roundTripsWithRandomNonceAndAuthenticatesIdentityAndSchema() {
    byte[] clear="{\"documentNumber\":\"000012345678\"}".getBytes(StandardCharsets.UTF_8);
    byte[] first=crypto.encrypt(clear,context),second=crypto.encrypt(clear,context);
    assertFalse(Arrays.equals(first,second));
    assertArrayEquals(clear,crypto.decrypt(first,context));
    assertFalse(new String(first,StandardCharsets.UTF_8).contains("000012345678"));
    for(var other:List.of(new ReviewEncryptionService.Context("other",1,2,3,1),
        new ReviewEncryptionService.Context("r1",99,2,3,1),new ReviewEncryptionService.Context("r1",1,99,3,1),
        new ReviewEncryptionService.Context("r1",1,2,99,1),new ReviewEncryptionService.Context("r1",1,2,3,2)))
      assertEquals("REVIEW_INVALID",assertThrows(BookingException.class,()->crypto.decrypt(first,other)).code);
    first[first.length-1]^=1;
    assertThrows(BookingException.class,()->crypto.decrypt(first,context));
    assertThrows(BookingException.class,()->crypto.decrypt(new byte[1],context));
  }
  @Test
  void requiresConfigured256BitKeyAndRejectsWrongKey() {
    assertEquals(503,assertThrows(BookingException.class,()->new ReviewEncryptionService("","v1")
        .encrypt(new byte[0],context)).status);
    byte[] key=new byte[32]; Arrays.fill(key,(byte)1);
    var other=new ReviewEncryptionService(Base64.getEncoder().encodeToString(key),"test-v1");
    assertThrows(BookingException.class,()->other.decrypt(crypto.encrypt(new byte[0],context),context));
  }
}
