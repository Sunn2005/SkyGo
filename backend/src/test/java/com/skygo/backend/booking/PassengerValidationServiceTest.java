package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.BookingQuote;
import com.skygo.backend.booking.model.BookingQuote.*;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.service.PassengerValidationService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PassengerValidationServiceTest {
  final PassengerValidationService service=new PassengerValidationService(Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC));
  final BookingReviewRequest.Contact contact=new BookingReviewRequest.Contact("test@example.com","090 123 4567");
  @Test
  void ageUsesDepartureBirthdayAndNormalizesContactWithoutLosingDocumentZeros() {
    var p=passenger(PassengerType.ADULT,Title.MR,"2018-01-02",DocumentType.PERSONAL_ID);
    var result=service.validate(List.of(p),contact,quote(false));
    assertEquals("+84901234567",result.contact().phoneNumber());
    assertEquals("000012345678",result.passengers().getFirst().document().number());
    var younger=passenger(PassengerType.ADULT,Title.MR,"2018-01-03",DocumentType.PERSONAL_ID);
    assertEquals("PASSENGER_AGE_MISMATCH",assertThrows(BookingException.class,()->service.validate(List.of(younger),contact,quote(false))).code);
  }
  @Test
  void internationalRequiresPassportAndValidIssuerAndNationality() {
    var id=passenger(PassengerType.ADULT,Title.MR,"1990-01-01",DocumentType.PERSONAL_ID);
    assertThrows(BookingException.class,()->service.validate(List.of(id),contact,quote(true)));
    var passport=passenger(PassengerType.ADULT,Title.MR,"1990-01-01",DocumentType.PASSPORT);
    assertDoesNotThrow(()->service.validate(List.of(passport),contact,quote(true)));
    // Valid until departure is enough; no additional six-month rule.
    assertEquals("****",PassengerValidationService.mask("1234"));
    assertEquals("********5678",PassengerValidationService.mask("000012345678"));
    assertFalse(passport.toString().contains("000012345678"));
  }
  @Test
  void rejectsDuplicateIdsUnexpectedTitlesAndFutureBirthdays() {
    var adult=passenger(PassengerType.ADULT,Title.MR,"1990-01-01",DocumentType.PERSONAL_ID);
    assertThrows(BookingException.class,()->service.validate(List.of(adult,adult),contact,quote(false)));
    assertThrows(BookingException.class,()->service.validate(List.of(passenger(PassengerType.ADULT,null,"1990-01-01",DocumentType.PERSONAL_ID)),contact,quote(false)));
    assertThrows(BookingException.class,()->service.validate(List.of(passenger(PassengerType.INFANT,null,"2030-01-02",DocumentType.PERSONAL_ID)),contact,quote(false)));
  }
  PassengerRequest passenger(PassengerType type,Title title,String birth,DocumentType document) {
    return new PassengerRequest("p1",type,title," An "," Nguyen ",LocalDate.parse(birth),Gender.MALE,
        new Document(document,"000012345678","VN",document==DocumentType.PASSPORT?"VN":null,
            document==DocumentType.PASSPORT?LocalDate.parse("2030-01-02"):null));
  }
  BookingQuote quote(boolean international) {
    var origin=new Airport("AAA","A","A","VN","UTC");
    var destination=new Airport("BBB","B","B",international?"SG":"VN","UTC");
    var date=Instant.parse("2030-01-02T00:00:00Z");
    return new BookingQuote(TripType.ONE_WAY,new PassengerCountsRequest(1,0,0),List.of(new BookingQuote.Leg(Direction.OUTBOUND,
        1,"f","SG1","SG","SkyGo",CabinClass.ECONOMY,origin,destination,date,date.plusSeconds(3600),60,
        international?"INTERNATIONAL":"DOMESTIC",1,1,100,510000,List.of(),List.of())),510000,new BigDecimal("0.1"),51000,561000,"VND");
  }
}
