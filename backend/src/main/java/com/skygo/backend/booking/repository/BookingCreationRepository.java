package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.model.*;
import com.skygo.backend.booking.model.BookingReviewSnapshot.*;
import com.skygo.backend.booking.model.SeatHold.Direction;
import com.skygo.backend.booking.service.BookingDocumentEncryptionService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.*;

@Repository
public class BookingCreationRepository {
  private final JdbcTemplate jdbc;
  private final SeatHoldRepository holds;
  private final BookingReviewRepository reviews;
  private final BookingDocumentEncryptionService encryption;
  private final Clock clock;

  public BookingCreationRepository(JdbcTemplate jdbc,SeatHoldRepository holds,BookingReviewRepository reviews,
      BookingDocumentEncryptionService encryption,Clock clock) {
    this.jdbc=jdbc; this.holds=holds; this.reviews=reviews; this.encryption=encryption; this.clock=clock;
  }

  public record Existing(long id,byte[] requestHash) {}

  public Optional<Existing> existing(long user,String key) {
    return jdbc.query("SELECT id,request_hash FROM booking WHERE user_id=? AND idempotency_key=? FOR UPDATE",
        (rs,n)->new Existing(rs.getLong("id"),rs.getBytes("request_hash")),user,key).stream().findFirst();
  }

  public BookingCreateResponse response(long bookingId) {
    var booking=jdbc.query("""
        SELECT b.public_id,b.booking_code,b.booking_status,b.created_at_utc,b.currency,b.total_amount,
          p.public_id AS payment_public_id,p.method,p.status AS payment_status,p.amount,p.currency AS payment_currency,
          p.transaction_code,p.paid_at_utc,
          h.public_id AS hold_public_id,h.expires_at_utc AS hold_expires_at_utc
        FROM booking b JOIN payment p ON p.booking_id=b.id
        LEFT JOIN seat_hold h ON h.booking_id=b.id AND h.status='ACTIVE'
        WHERE b.id=?
        """,(rs,n)->new BookingCreateResponse(rs.getString("public_id"),rs.getString("booking_code"),
        rs.getString("booking_status"),instant(rs,"created_at_utc"),rs.getString("currency"),
        rs.getString("hold_public_id"),nullableInstant(rs,"hold_expires_at_utc"),rs.getLong("total_amount"),
        List.of(),new BookingCreateResponse.Payment(rs.getString("payment_public_id"),rs.getString("method"),
            rs.getString("payment_status"),rs.getLong("amount"),rs.getString("payment_currency"),
            rs.getString("transaction_code"),nullableInstant(rs,"paid_at_utc"))),bookingId).getFirst();
    var passengers=jdbc.query("""
        SELECT bp.public_id,bp.client_passenger_id,t.ticket_number
        FROM booking_passenger bp LEFT JOIN ticket t ON t.booking_passenger_id=bp.id
        WHERE bp.booking_id=? ORDER BY bp.id
        """,(rs,n)->new BookingCreateResponse.Passenger(rs.getString("public_id"),
        rs.getString("client_passenger_id"),rs.getString("ticket_number")),bookingId);
    return new BookingCreateResponse(booking.bookingId(),booking.bookingCode(),booking.bookingStatus(),
        booking.createdAt(),booking.currency(),booking.seatHoldId(),booking.holdExpiresAt(),
        booking.totalAmount(),passengers,booking.payment());
  }

  public BookingCreateResponse create(long user,String key,byte[] hash,
      BookingReviewRepository.StoredReview review,SeatHold hold,BookingReviewSnapshot snapshot,Instant now) {
    String publicId=UUID.randomUUID().toString();
    String code="SG"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT);
    var quote=snapshot.quote();
    var counts=quote.passengerCounts();
    var price=snapshot.price();
    insert("booking", "public_id,booking_code,user_id,review_id,seat_hold_id,idempotency_key,request_hash,"
        +"trip_type,adults,children,infants,contact_email,contact_phone,currency,fare_subtotal,"
        +"extra_baggage_amount,vat_rate,vat_amount,total_amount,booking_status,created_at_utc,updated_at_utc",
        publicId,code,user,review.id(),hold.id(),key,hash,quote.tripType().name(),counts.adults(),
        counts.children(),counts.infants(),snapshot.contact().email(),snapshot.contact().phoneNumber(),
        quote.currency(),price.fareSubtotal(),price.extraBaggageAmount(),price.vatRate(),price.vatAmount(),
        price.totalAmount(),"PENDING_PAYMENT",utc(now),utc(now));
    long booking=id("booking",publicId);

    var legIds=new EnumMap<Direction,Long>(Direction.class);
    for(var leg:quote.legs()) {
      insert("booking_leg", "booking_id,direction,flight_fare_id,flight_public_id,flight_number,airline_code,"
          +"airline_name,cabin_class,origin_code,origin_name,origin_city,origin_country_code,origin_time_zone,"
          +"destination_code,destination_name,destination_city,destination_country_code,destination_time_zone,"
          +"departure_at_utc,arrival_at_utc,duration_minutes,route_type",
          booking,leg.direction().name(),leg.fareId(),leg.flightId(),leg.flightNumber(),leg.airlineCode(),
          leg.airlineName(),leg.cabinClass().name(),leg.origin().code(),leg.origin().name(),leg.origin().city(),
          leg.origin().countryCode(),leg.origin().timeZone(),leg.destination().code(),leg.destination().name(),
          leg.destination().city(),leg.destination().countryCode(),leg.destination().timeZone(),
          utc(leg.departureAt()),utc(leg.arrivalAt()),leg.durationMinutes(),leg.routeType());
      long legId=jdbc.queryForObject("SELECT id FROM booking_leg WHERE booking_id=? AND direction=?",
          Long.class,booking,leg.direction().name());
      legIds.put(leg.direction(),legId);
      for(var fare:leg.fareLines())
        insert("booking_fare_line","booking_id,booking_leg_id,passenger_type,quantity,unit_base_fare,fare_subtotal",
            booking,legId,fare.passengerType().name(),fare.quantity(),fare.unitBaseFare(),fare.fareSubtotal());
    }

    var passengerIds=new HashMap<String,Long>();
    for(var p:snapshot.passengers()) {
      String passengerId=UUID.randomUUID().toString();
      String number=p.document().number();
      String last4=number.length()>4?number.substring(number.length()-4):null;
      insert("booking_passenger","public_id,booking_id,client_passenger_id,passenger_type,title,given_names,"
          +"surname,date_of_birth,gender,document_type,document_number_ciphertext,document_number_last4,"
          +"issuing_country_code,nationality_country_code,document_expires_on",
          passengerId,booking,p.clientPassengerId(),p.passengerType().name(),
          p.title()==null?null:p.title().name(),p.givenNames(),p.surname(),p.dateOfBirth(),
          p.gender().name(),p.document().documentType().name(),encryption.encrypt(number,publicId,passengerId),
          last4,p.document().issuingCountryCode(),p.document().nationalityCountryCode(),p.document().expiresOn());
      passengerIds.put(p.clientPassengerId(),id("booking_passenger",passengerId));
    }

    for(var row:snapshot.passengerBenefits()) {
      var leg=snapshot.baggage().stream().filter(b->b.direction()==row.direction()).findFirst().orElseThrow();
      var selected=leg.passengers().stream().filter(p->p.clientPassengerId().equals(row.clientPassengerId())).findFirst().orElseThrow();
      var benefit=row.benefit();
      var policy=leg.policy();
      insert("booking_passenger_benefit","booking_id,booking_passenger_id,booking_leg_id,carry_on_max_kg,"
          +"checked_pieces,checked_baggage_policy_id,checked_piece_max_kg,checked_piece_max_linear_cm,"
          +"extra_checked_pieces,extra_unit_price_excl_vat,extra_baggage_amount,included_meals_per_leg,"
          +"seat_type,stroller_pieces,stroller_max_kg",
          booking,passengerIds.get(row.clientPassengerId()),legIds.get(row.direction()),benefit.carryOnMaxKg(),
          benefit.checkedPieces(),policy.policyId(),benefit.checkedPieceMaxKg(),policy.maxLinearDimensionsCm(),
          selected.extraPieces(),policy.extraPiecePriceExclVat(),selected.extraAmountExclVat(),
          benefit.includedMealsPerLeg(),benefit.seatType(),benefit.strollerPieces(),benefit.strollerMaxKg());
    }

    insert("payment","public_id,booking_id,method,status,amount,currency,created_at_utc,updated_at_utc",
        UUID.randomUUID().toString(),booking,"QR_SIMULATION","PENDING",price.totalAmount(),quote.currency(),
        utc(now),utc(now));
    var finalTime=clock.instant();
    if(!holds.attachBooking(hold.id(),user,booking,finalTime))
      throw new BookingException(409,"SEAT_HOLD_EXPIRED","Lượt giữ chỗ đã hết hiệu lực. Vui lòng chọn lại chuyến bay.");
    finalTime=clock.instant();
    if(!reviews.markUsed(review.id(),user,snapshot.sessionId(),review.version(),finalTime))
      throw new BookingException(409,"REVIEW_EXPIRED","Bản rà soát đã hết hạn. Vui lòng xác nhận lại thông tin.");
    return response(booking);
  }

  private long id(String table,String publicId) {
    return jdbc.queryForObject("SELECT id FROM "+table+" WHERE public_id=?",Long.class,publicId);
  }
  private void insert(String table,String columns,Object... values) {
    String marks=String.join(",",Collections.nCopies(values.length,"?"));
    jdbc.update("INSERT INTO "+table+" ("+columns+") VALUES ("+marks+")",values);
  }
  private static Instant nullableInstant(ResultSet rs,String column) throws SQLException {
    return rs.getObject(column)==null?null:instant(rs,column);
  }
}
