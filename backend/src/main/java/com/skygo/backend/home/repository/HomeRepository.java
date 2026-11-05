package com.skygo.backend.home.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HomeRepository {
  private final JdbcTemplate jdbc;

  public HomeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record RouteRow(
      long routeId,
      String originCode,
      String originCity,
      String destinationCode,
      String destinationCity,
      String destinationCoverImagePath,
      long priceFromVnd,
      long monthlyPaidBookings) {}

  public record UpcomingRow(
      String bookingId,
      String bookingCode,
      String flightNumber,
      String originCode,
      String originCity,
      String originTimeZone,
      String destinationCode,
      String destinationCity,
      String destinationTimeZone,
      Instant departureAtUtc,
      Instant arrivalAtUtc) {}

  public List<RouteRow> availableRoutes(Instant monthStartUtc, Instant monthEndUtc, Instant now) {
    String sql =
        """
        SELECT route.id AS route_id, origin.code AS origin_code,
               origin.city AS origin_city, destination.code AS destination_code,
               destination.city AS destination_city,
               destination.cover_image_path AS destination_cover_image_path,
               MIN(fare.adult_base_fare + ROUND(fare.adult_base_fare * policy.vat_rate, 0)) AS price_from_vnd,
               COALESCE(popularity.paid_count, 0) AS paid_count
          FROM air_route route
          JOIN airport origin ON origin.id = route.origin_airport_id
          JOIN airport destination ON destination.id = route.destination_airport_id
          JOIN flight flight ON flight.route_id = route.id
          JOIN flight_fare fare ON fare.flight_id = flight.id
          JOIN pricing_policy policy ON policy.id = fare.pricing_policy_id
          LEFT JOIN (
              SELECT leg.origin_code, leg.destination_code, COUNT(*) AS paid_count
                FROM booking_leg leg
                JOIN booking booking ON booking.id = leg.booking_id
                JOIN payment payment ON payment.booking_id = booking.id
               WHERE booking.booking_status = 'PAID'
                 AND payment.status = 'SUCCEEDED'
                 AND payment.paid_at_utc >= ? AND payment.paid_at_utc < ?
               GROUP BY leg.origin_code, leg.destination_code
          ) popularity ON popularity.origin_code = origin.code
                      AND popularity.destination_code = destination.code
         WHERE route.is_active = 1 AND origin.is_active = 1 AND destination.is_active = 1
           AND flight.sale_status = 'ON_SALE' AND flight.departure_at_utc > ?
           AND fare.cabin_class = 'ECONOMY' AND fare.is_on_sale = 1
           AND fare.available_seats > 0
         GROUP BY route.id, origin.code, origin.city,
                  destination.code, destination.city, destination.cover_image_path,
                  popularity.paid_count
         ORDER BY paid_count DESC,
                  CASE WHEN origin.code = 'SGN' AND destination.code = 'HAN' THEN 0
                       WHEN origin.code = 'HAN' AND destination.code = 'DAD' THEN 1
                       WHEN origin.code = 'SGN' AND destination.code = 'PQC' THEN 2
                       ELSE 3 END,
                  origin.code, destination.code
        """;
    return jdbc.query(
        sql,
        (row, n) ->
            new RouteRow(
                row.getLong("route_id"),
                row.getString("origin_code"),
                row.getString("origin_city"),
                row.getString("destination_code"),
                row.getString("destination_city"),
                row.getString("destination_cover_image_path"),
                row.getLong("price_from_vnd"),
                row.getLong("paid_count")),
        LocalDateTime.ofInstant(monthStartUtc, ZoneOffset.UTC),
        LocalDateTime.ofInstant(monthEndUtc, ZoneOffset.UTC),
        LocalDateTime.ofInstant(now, ZoneOffset.UTC));
  }

  public Optional<UpcomingRow> nextConfirmedFlight(long userId, Instant now) {
    String sql =
        """
        SELECT booking.public_id AS booking_id, booking.booking_code, leg.flight_number,
               leg.origin_code, leg.origin_city, leg.origin_time_zone,
               leg.destination_code, leg.destination_city, leg.destination_time_zone,
               leg.departure_at_utc, leg.arrival_at_utc
          FROM booking booking
          JOIN booking_leg leg ON leg.booking_id = booking.id
          JOIN payment payment ON payment.booking_id = booking.id
         WHERE booking.user_id = ? AND booking.booking_status = 'PAID'
           AND payment.status = 'SUCCEEDED' AND leg.departure_at_utc > ?
           AND EXISTS (SELECT 1 FROM ticket ticket WHERE ticket.booking_id = booking.id)
         ORDER BY leg.departure_at_utc, booking.id, leg.id
         LIMIT 1
        """;
    return jdbc
        .query(
            sql,
            (row, n) ->
                new UpcomingRow(
                    row.getString("booking_id"),
                    row.getString("booking_code"),
                    row.getString("flight_number"),
                    row.getString("origin_code"),
                    row.getString("origin_city"),
                    row.getString("origin_time_zone"),
                    row.getString("destination_code"),
                    row.getString("destination_city"),
                    row.getString("destination_time_zone"),
                    row.getObject("departure_at_utc", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC),
                    row.getObject("arrival_at_utc", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC)),
            userId,
            LocalDateTime.ofInstant(now, ZoneOffset.UTC))
        .stream()
        .findFirst();
  }
}
