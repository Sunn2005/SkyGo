package com.skygo.backend.ai.chat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.skygo.backend.ai.chat.AiToolInputs.*;
import com.skygo.backend.ai.chat.contract.AiChatResponse.Airport;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AiReadRepositoryTest {
  private static final Airport SGN = new Airport("SGN", "Sân bay quốc tế Tân Sơn Nhất", "TP. Hồ Chí Minh", "Việt Nam", "Asia/Ho_Chi_Minh");
  private static final Airport PQC = new Airport("PQC", "Sân bay quốc tế Phú Quốc", "Phú Quốc", "Việt Nam", "Asia/Ho_Chi_Minh");

  @Test
  void recognizesNaturalHoChiMinhCityNamesWithoutRequiringAnIataCode() {
    for (String name : List.of("thành phố Hồ Chí Minh", "Thành phố Hồ Chí Minh", "TP. Hồ Chí Minh",
        "TP.HCM", "TP HCM", "TPHCM", "Hồ Chí Minh", "Sài Gòn", "Sai Gon", "HCMC",
        "  thanh   pho ho chi minh  ", "Ho Chi Minh City", "sân bay SGN")) {
      assertThat(AiReadRepository.resolve(name, "origin", List.of(SGN, PQC))).as(name).isEqualTo(SGN);
    }
  }

  @Test
  void recognizesCityAndAirportNamePresentationVariants() {
    for (String name : List.of("Phú Quốc", "Phu Quoc", "thành phố Phú Quốc", "TP. Phú Quốc",
        "sân bay Phú Quốc", "Sân bay quốc tế Phú Quốc", "Phu Quoc International Airport", "PQC")) {
      assertThat(AiReadRepository.resolve(name, "destination", List.of(SGN, PQC))).as(name).isEqualTo(PQC);
    }
    for (String name : List.of("Tân Sơn Nhất", "sân bay Tân Sơn Nhất", "Tan Son Nhat International Airport")) {
      assertThat(AiReadRepository.resolve(name, "origin", List.of(SGN, PQC))).as(name).isEqualTo(SGN);
    }
    var han = new Airport("HAN", "Sân bay quốc tế Nội Bài", "Hà Nội", "Việt Nam", "Asia/Ho_Chi_Minh");
    assertThat(AiReadRepository.resolve("thành phố Hà Nội", "destination", List.of(SGN, PQC, han))).isEqualTo(han);
    assertThat(AiReadRepository.resolve("sân bay Nội Bài", "destination", List.of(SGN, PQC, han))).isEqualTo(han);
  }

  @Test
  void cityAliasesDoNotPickAnAirportWhenMultipleActiveChoicesServeTheCity() {
    var second = new Airport("ALT", "Another Airport", "Thành phố Hồ Chí Minh", "Việt Nam", "Asia/Ho_Chi_Minh");
    for (String name : List.of("thành phố Hồ Chí Minh", "Sài Gòn", "HCM")) {
      var clarification = org.junit.jupiter.api.Assertions.assertThrows(AiClarification.class,
          () -> AiReadRepository.resolve(name, "origin", List.of(SGN, second, PQC)));
      assertThat(clarification.detail().airportOptions()).containsExactly(SGN, second);
    }
    assertThat(AiReadRepository.resolve("SGN", "origin", List.of(SGN, second, PQC))).isEqualTo(SGN);
  }

  @Test
  void aliasesRespectTheActualCatalogAndExplicitAirportCodesTakePriority() {
    assertThatThrownBy(() -> AiReadRepository.resolve("Sài Gòn", "origin", List.of(PQC)))
        .isInstanceOf(AiClarification.class);
    var codedHcm = new Airport("HCM", "Airport coded HCM", "Another city", "Test", "UTC");
    assertThat(AiReadRepository.resolve("HCM", "origin", List.of(SGN, codedHcm))).isEqualTo(codedHcm);
    assertThat(AiReadRepository.resolve("TP. HCM", "origin", List.of(SGN, codedHcm))).isEqualTo(SGN);
    assertThat(AiReadRepository.resolve("thành phố HCM", "origin", List.of(SGN, codedHcm))).isEqualTo(SGN);
    for (String value : List.of("sân bay", "thành phố", "???", "địa danh chưa có")) {
      var clarification = org.junit.jupiter.api.Assertions.assertThrows(AiClarification.class,
          () -> AiReadRepository.resolve(value, "destination", List.of(SGN, PQC)));
      assertThat(clarification.detail().airportOptions()).isEmpty();
      assertThat(clarification.getMessage()).contains("điểm đến", "thành phố").doesNotContain("mã IATA");
    }
  }

  @Test
  void naturalRouteNamesBindTheCorrectAirportsForFlightSearch() {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var repository = new AiReadRepository(jdbc, Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC));
    when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Airport>>any()))
        .thenReturn(List.of(SGN, PQC));
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
        .thenReturn(List.of());
    var result = repository.flights(new FlightQuery("thành phố Hồ Chí Minh", "Phú Quốc",
        LocalDate.of(2026, 12, 2), 1, 0, 0, Cabin.ANY, Sort.PRICE_ASC, 5));
    assertThat(result.origin()).isEqualTo(SGN);
    assertThat(result.destination()).isEqualTo(PQC);
    var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).query(anyString(), params.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
    assertThat(params.getValue().getValue("origin")).isEqualTo("SGN");
    assertThat(params.getValue().getValue("destination")).isEqualTo("PQC");
  }

  @Test
  void cityResolutionRequiresChoosingBetweenMultipleAirports() {
    var a = new Airport("HND", "Haneda", "Tokyo", "Japan", "Asia/Tokyo");
    var b = new Airport("NRT", "Narita", "Tokyo", "Japan", "Asia/Tokyo");
    assertThat(AiReadRepository.resolve("HND", "origin", List.of(a, b))).isEqualTo(a);
    var error = org.junit.jupiter.api.Assertions.assertThrows(AiClarification.class,
        () -> AiReadRepository.resolve("Tokyo", "origin", List.of(a, b)));
    assertThat(error.detail().airportOptions()).containsExactly(a, b);
    assertThatThrownBy(() -> AiReadRepository.resolve("' OR 1=1 --", "origin", List.of(a, b)))
        .isInstanceOf(AiClarification.class);
  }

  @Test
  void sqlRanksAllFaresBeforeLimitingAndUsesTheOriginDayIncludingDst() {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var repository = new AiReadRepository(jdbc, Clock.fixed(Instant.parse("2027-03-14T01:00:00Z"), ZoneOffset.UTC));
    when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<Airport>>any())).thenReturn(List.of(
        new Airport("JFK", "JFK", "New York", "USA", "America/New_York"),
        new Airport("HND", "Haneda", "Tokyo", "Japan", "Asia/Tokyo")));
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
        .thenReturn(List.of());
    var result = repository.flights(new FlightQuery("JFK", "HND", LocalDate.of(2027, 3, 14), 2, 1, 1, Cabin.ANY, Sort.PRICE_ASC, 5));
    assertThat(result.options()).isEmpty();
    var sql = ArgumentCaptor.forClass(String.class);
    var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).query(sql.capture(), params.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
    assertThat(params.getValue().getValue("dayStart")).isEqualTo(LocalDateTime.of(2027, 3, 14, 5, 0));
    assertThat(params.getValue().getValue("dayEnd")).isEqualTo(LocalDateTime.of(2027, 3, 15, 4, 0));
    assertThat(params.getValue().getValue("seats")).isEqualTo(3);
    assertThat(sql.getValue()).contains("COUNT(*) OVER()", "base_total+ROUND(base_total*vat_rate,0)", "ORDER BY\nparty_total")
        .doesNotContain("FOR UPDATE", "LOCK IN SHARE MODE", "OFFSET");
    assertThat(sql.getValue().indexOf("ORDER BY")).isLessThan(sql.getValue().indexOf("LIMIT :limit"));
  }

  @Test
  void recentBookingSqlAlwaysBindsAuthenticatedOwnerAndDateWindow() {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var now = Instant.parse("2026-11-10T12:00:00Z");
    var repository = new AiReadRepository(jdbc, Clock.fixed(now, ZoneOffset.UTC));
    when(jdbc.query(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
        .thenReturn(List.of());
    repository.bookings(73L, new BookingQuery(15, 3, BookingStatus.PAID));
    var sql = ArgumentCaptor.forClass(String.class);
    var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).query(sql.capture(), params.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
    assertThat(sql.getValue()).contains("b.user_id=:owner", "b.created_at_utc>=:since", "ORDER BY b.created_at_utc DESC,b.id DESC")
        .doesNotContain("b.*", "contact_email", "contact_phone", "booking_passenger");
    assertThat(params.getValue().getValue("owner")).isEqualTo(73L);
    assertThat(params.getValue().getValue("since")).isEqualTo(LocalDateTime.of(2026, 10, 26, 12, 0));
  }
}
