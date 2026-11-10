package com.skygo.backend.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.skygo.backend.booking.contract.BookingCreateRequest;
import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import com.skygo.backend.booking.contract.SeatHoldLegRequest;
import com.skygo.backend.booking.model.SeatHold.CabinClass;
import com.skygo.backend.booking.model.SeatHold.TripType;
import com.skygo.backend.booking.service.BookingCreationService;
import com.skygo.backend.booking.service.BookingReviewService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named = "SKYGO_TEST_DB_URL", matches = ".+")
class BookingListIntegrationTest extends PricingFixture {
  @Autowired BookingReviewService reviews;
  @Autowired BookingCreationService bookings;

  @Test
  void listsOnlyOwnedBookingsWithBothLegsAndNoPassengerDetails() throws Exception {
    var hold = hold(TripType.ROUND_TRIP, 1, 1, 0);
    service.leg(user, hold.seatHoldId(), "OUTBOUND",
        new SeatHoldLegRequest(outFlight, CabinClass.BUSINESS));
    hold = service.leg(user, hold.seatHoldId(), "RETURN",
        new SeatHoldLegRequest(backFlight, CabinClass.ECONOMY));
    var session = session(user);
    var review = reviews.create(session, reviewRequest(hold,
        List.of(passenger("a", PassengerType.ADULT), passenger("c", PassengerType.CHILD))));
    var created = bookings.create(session, "list-booking",
        new BookingCreateRequest(review.reviewId(), review.version()));

    mvc.perform(get("/api/v1/bookings").with(asSession(session)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.pagination.totalElements").value(1))
        .andExpect(jsonPath("$.pagination.totalPages").value(1))
        .andExpect(jsonPath("$.data[0].bookingId").value(created.booking().bookingId()))
        .andExpect(jsonPath("$.data[0].bookingStatus").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.data[0].paymentStatus").value("PENDING"))
        .andExpect(jsonPath("$.data[0].passengerCounts.adults").value(1))
        .andExpect(jsonPath("$.data[0].passengerCounts.children").value(1))
        .andExpect(jsonPath("$.data[0].legs.length()").value(2))
        .andExpect(jsonPath("$.data[0].legs[0].cabinClass").value("BUSINESS"))
        .andExpect(jsonPath("$.data[0].legs[1].cabinClass").value("ECONOMY"))
        .andExpect(jsonPath("$.data[0].legs[0].departureAt").exists())
        .andExpect(jsonPath("$.data[0].contact").doesNotExist())
        .andExpect(jsonPath("$.data[0].passengers").doesNotExist());
    mvc.perform(get("/api/v1/bookings?page=1&size=1").with(asSession(session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.pagination.totalElements").value(1));
    mvc.perform(get("/api/v1/bookings?status=pending").with(asSession(session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pagination.totalElements").value(1));
    mvc.perform(get("/api/v1/bookings?status=paid").with(asSession(session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pagination.totalElements").value(0));
    mvc.perform(get("/api/v1/bookings").with(asSession(session(otherUser))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
    mvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/bookings?size=101").with(asSession(session)))
        .andExpect(status().is(422));
    mvc.perform(get("/api/v1/bookings?status=unknown").with(asSession(session)))
        .andExpect(status().is(422));
  }
}
