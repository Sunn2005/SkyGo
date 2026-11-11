package com.skygo.backend.ai.index;

import static com.skygo.backend.ai.AiTestSupport.properties;
import static com.skygo.backend.ai.index.KnowledgeIndexerTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcKnowledgeSourceTest {
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final TransactionTemplate transaction = mock(TransactionTemplate.class);
  final JdbcKnowledgeSource source = new JdbcKnowledgeSource(jdbc, transaction, properties(), INDEX);

  JdbcKnowledgeSourceTest() {
    when(transaction.execute(any())).thenAnswer(call ->
        call.<TransactionCallback<List<Document>>>getArgument(0).doInTransaction(mock(TransactionStatus.class)));
  }

  @Test
  void buildsAllDocumentTypesWithCorrectVatLocalTimeAndPrivateBookingScope() throws Exception {
    var values = fixture();
    ResultSet row = mock(ResultSet.class);
    when(row.getString(anyString())).thenAnswer(call -> {
      Object value = values.get(call.<String>getArgument(0));
      return value == null ? null : value.toString();
    });
    when(row.getInt(anyString())).thenAnswer(call -> ((Number) values.get(call.<String>getArgument(0))).intValue());
    when(row.getLong(anyString())).thenAnswer(call -> ((Number) values.get(call.<String>getArgument(0))).longValue());
    when(row.getBigDecimal(anyString())).thenAnswer(call -> values.get(call.<String>getArgument(0)));
    when(row.getObject(anyString(), eq(LocalDateTime.class))).thenAnswer(call -> values.get(call.<String>getArgument(0)));
    when(row.getObject(anyString())).thenAnswer(call -> values.get(call.<String>getArgument(0)));
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Document>>any(), any(Object[].class)))
        .thenAnswer(call -> List.of(call.<RowMapper<Document>>getArgument(1).mapRow(row, 0)));
    var docs = source.snapshot(NOW);
    assertThat(docs).hasSize(7);
    assertThat(docs).extracting(doc -> doc.getMetadata().get("document_type")).containsExactly(
        "airport", "route", "flight_fare", "fare_benefit", "passenger_fare_rule", "baggage_policy", "booking_summary");
    var fare = docs.get(2);
    assertThat(fare.getText()).contains("1080000 VND", "2026-11-11T09:00+07:00", "phổ thông", "đã gồm VAT");
    assertThat(fare.getMetadata()).containsEntry("price_vnd", 1080000L).containsEntry("scope", "public");
    assertThat(docs.get(3).getText()).contains("23 kg", "158 cm");
    assertThat(docs.get(5).getText()).contains("325000 VND CHƯA VAT");
    var booking = docs.get(6);
    assertThat(booking.getMetadata()).containsEntry("scope", "private").containsEntry("owner_id", 42L)
        .containsEntry("booking_id", "flight-or-booking-public-id");
    assertThat(booking.getText()).contains("TOÀN ĐƠN").doesNotContain("contact_email", "document_number", "owner_id");
    assertThat(docs.subList(0, 6)).allSatisfy(doc -> assertThat(doc.getMetadata()).doesNotContainKey("owner_id"));

    var sql = ArgumentCaptor.forClass(String.class);
    var arguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, times(7)).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<Document>>any(), arguments.capture());
    assertThat(sql.getAllValues()).allSatisfy(query -> assertThat(query)
        .doesNotContain("SELECT *", "b.*", "contact_email", "contact_phone", "booking_passenger", "app_user", "token", "password")
        .endsWith(" LIMIT ?"));
    assertThat(arguments.getAllValues().get(2)[0]).isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 0));
  }

  @Test
  void emptyDatabaseDoesNotInventFlightOrBookingData() {
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Document>>any(), any(Object[].class)))
        .thenReturn(List.of());
    assertThat(source.snapshot(NOW)).isEmpty();
  }

  @Test
  void rejectsTruncatedReadsInsteadOfTreatingThemAsCompleteSnapshots() {
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Document>>any(), any(Object[].class)))
        .thenReturn(java.util.Collections.nCopies(101, KnowledgeIndexerTest.doc("1", "value", null, NOW)));
    assertThatThrownBy(() -> source.snapshot(NOW)).hasMessageContaining("max-documents");
  }

  @Test
  void adultPriceUsesTheSameHalfUpVatCalculationAsFlightSearch() {
    assertThat(JdbcKnowledgeSource.adultPriceIncludingVat(new BigDecimal("1000005"), new BigDecimal("0.1")))
        .isEqualTo(1100006L);
  }

  private Map<String, Object> fixture() {
    var values = new HashMap<String, Object>();
    values.putAll(Map.of("code", "SGN", "name", "Tân Sơn Nhất", "city", "TP. Hồ Chí Minh",
        "country_code", "VN", "country_name", "Việt Nam", "region_name", "Việt Nam",
        "time_zone", "Asia/Ho_Chi_Minh", "id", 1, "air_distance_km", 1160));
    values.putAll(Map.of("origin_code", "SGN", "origin_city", "TP. Hồ Chí Minh", "origin_time_zone", "Asia/Ho_Chi_Minh",
        "destination_code", "HAN", "destination_city", "Hà Nội", "destination_time_zone", "Asia/Ho_Chi_Minh",
        "public_id", "flight-or-booking-public-id", "flight_number", "SG101", "fare_id", 7, "cabin_class", "ECONOMY"));
    values.putAll(Map.of("adult_base_fare", new BigDecimal("1000000"), "vat_rate", new BigDecimal("0.08"),
        "currency", "VND", "available_seats", 8, "passenger_type", "ADULT", "carry_on_max_kg", 7,
        "checked_pieces", 1, "included_meals_per_leg", 1, "seat_type", "STANDARD"));
    values.putAll(Map.of("stroller_pieces", 0, "stroller_max_kg", 0, "piece_max_kg", 23,
        "max_linear_dimensions_cm", 158, "route_scope", "VN_DOMESTIC", "multiplier", BigDecimal.ONE,
        "version", 1, "max_extra_pieces", 2, "extra_piece_price_excl_vat", 325000L));
    values.putAll(Map.of("user_id", 42L, "booking_status", "PAID", "payment_status", "SUCCEEDED",
        "direction", "OUTBOUND", "adults", 1, "children", 0, "infants", 0, "total_amount", 1080000L));
    values.put("departure_at_utc", LocalDateTime.of(2026, 11, 11, 2, 0));
    values.put("arrival_at_utc", LocalDateTime.of(2026, 11, 11, 4, 0));
    values.put("effective_from_utc", LocalDateTime.of(2026, 1, 1, 0, 0));
    values.put("created_at_utc", LocalDateTime.of(2026, 11, 9, 3, 0));
    return values;
  }
}
