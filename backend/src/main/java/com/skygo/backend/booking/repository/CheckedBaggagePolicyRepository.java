package com.skygo.backend.booking.repository;

import com.skygo.backend.booking.contract.PassengerRequest.PassengerType;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.skygo.backend.booking.repository.SeatHoldRepository.instant;

@Repository
public class CheckedBaggagePolicyRepository {
  private final JdbcTemplate jdbc;

  public CheckedBaggagePolicyRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

  public record Policy(long id, int version, int pieceMaxKg, int maxLinearDimensionsCm,
      int maxExtraPieces, long extraPiecePriceExclVat, String currency,
      Instant effectiveFrom, Instant effectiveTo) {}
  public record FarePolicy(long fareId, PassengerType passengerType, Policy policy) {}

  public List<FarePolicy> forFares(List<Long> fares) {
    String placeholders=String.join(",",Collections.nCopies(fares.size(),"?"));
    return jdbc.query("""
        SELECT b.flight_fare_id,b.passenger_type,p.*
        FROM fare_benefit_policy b JOIN checked_baggage_policy p ON p.id=b.checked_baggage_policy_id
        WHERE b.flight_fare_id IN (
        """+placeholders+") ORDER BY b.flight_fare_id,b.passenger_type LOCK IN SHARE MODE",
        (rs,n)->new FarePolicy(rs.getLong("flight_fare_id"),PassengerType.valueOf(rs.getString("passenger_type")),
            new Policy(rs.getLong("id"),rs.getInt("version"),rs.getInt("piece_max_kg"),
                rs.getInt("max_linear_dimensions_cm"),rs.getInt("max_extra_pieces"),
                rs.getLong("extra_piece_price_excl_vat"),rs.getString("currency"),instant(rs,"effective_from_utc"),
                rs.getObject("effective_to_utc")==null?null:instant(rs,"effective_to_utc"))),fares.toArray());
  }
}
