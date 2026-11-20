package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.model.SeatHold.*;
import com.skygo.backend.booking.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@EnabledIfEnvironmentVariable(named="SKYGO_TEST_DB_URL",matches=".+")
@TestPropertySource(properties={"skygo.ai.enabled=false",
    "skygo.review.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "skygo.booking.document-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class PassengerPriceIntegrationTest extends PricingFixture {
  @Autowired BookingReviewService reviews;
  @Autowired BookingBaggageService baggage;
  @Autowired BookingCreationService bookings;
  @Autowired BookingDetailService details;

  @Test void roundTripIndividualPricesReconcileWithReviewAndPersistedBooking() {
    var h=hold(TripType.ROUND_TRIP,2,1,1);
    h=service.leg(user,h.seatHoldId(),"RETURN",new SeatHoldLegRequest(backFlight,CabinClass.ECONOMY));
    var session=session(user);
    var review=reviews.create(session,reviewRequest(h,List.of(passenger("a",PassengerType.ADULT),
        passenger("b",PassengerType.ADULT),passenger("c",PassengerType.CHILD),passenger("i",PassengerType.INFANT))));
    var selections=new ArrayList<BaggageSaveRequest.Selection>();
    for(var direction:Direction.values()) for(String id:List.of("a","b","c")) {
      int pieces=direction==Direction.OUTBOUND ? (id.equals("a")?1:0) : (id.equals("b")?2:id.equals("c")?1:0);
      selections.add(new BaggageSaveRequest.Selection(id,direction,pieces));
    }
    var saved=baggage.save(session,review.reviewId(),new BaggageSaveRequest(review.version(),selections));
    var expected=new HashMap<String,PassengerPrice>();
    saved.passengers().forEach(p->expected.put(p.clientPassengerId(),p.price()));
    assertEquals(PassengerPrice.of(1020000,325000),expected.get("a"));
    assertEquals(PassengerPrice.of(1020000,650000),expected.get("b"));
    assertEquals(PassengerPrice.of(918000,325000),expected.get("c"));
    assertEquals(PassengerPrice.of(102000,0),expected.get("i"));
    assertEquals(saved.price().fareSubtotal(),expected.values().stream().mapToLong(PassengerPrice::fareSubtotal).sum());
    assertEquals(saved.price().extraBaggageAmount(),expected.values().stream().mapToLong(PassengerPrice::extraBaggageAmount).sum());
    assertEquals(saved.totalAmount(),expected.values().stream().mapToLong(PassengerPrice::subtotal).sum()+saved.price().vatAmount());
    var created=bookings.create(session,"passenger-price",new BookingCreateRequest(saved.reviewId(),saved.version()));
    var detail=details.get(user,created.booking().bookingId());
    detail.passengers().forEach(p->assertEquals(expected.get(p.clientPassengerId()),p.price()));
    assertEquals(saved.totalAmount(),detail.price().totalAmount());
  }
}
