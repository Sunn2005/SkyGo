package com.skygo.backend.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;

@EnabledIfEnvironmentVariable(named = "SKYGO_TEST_DB_URL", matches = ".+")
@TestPropertySource(properties = {"skygo.ai.enabled=false",
    "skygo.booking.document-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class SavedPassengerIntegrationTest extends BookingFixture {
  private static final String BASE = "/api/v1/saved-passengers";
  private static final String BODY = """
      {"title":"MR","givenNames":"Van An","surname":"Nguyen","dateOfBirth":"1990-01-02","gender":"MALE",
       "document":{"documentType":"PERSONAL_ID","number":"000012345678","issuingCountryCode":"VN"}}
      """;

  @AfterEach void removeProfiles() {
    for (long owner : users) jdbc.update("DELETE FROM saved_passenger WHERE user_id=?", owner);
  }
  private String createProfile() throws Exception {
    mvc.perform(post(BASE).with(as(user)).contentType("application/json").content(BODY))
        .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.document.number").value("000012345678"));
    return jdbc.queryForObject("SELECT public_id FROM saved_passenger WHERE user_id=? ORDER BY id DESC LIMIT 1", String.class, user);
  }

  @Test void lifecycleProtectsStoredNumberAndListWhileDetailPrefillsAndUpdatePersists() throws Exception {
    String id = createProfile();
    assertFalse(new String(jdbc.queryForObject("SELECT document_number_ciphertext FROM saved_passenger WHERE public_id=?",
        byte[].class, id), StandardCharsets.UTF_8).contains("000012345678"));
    mvc.perform(get(BASE).with(as(user)))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data[0].documentNumberLast4").value("5678"))
        .andExpect(jsonPath("$.data[0].document").doesNotExist())
        .andExpect(jsonPath("$.data[0].documentNumber").doesNotExist());
    mvc.perform(get(BASE + "/" + id).with(as(user)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.document.number").value("000012345678"));
    mvc.perform(put(BASE + "/" + id).with(as(user)).contentType("application/json")
        .content(BODY.replace("Van An", "Van Binh").replace("000012345678", "000087654321")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.givenNames").value("Van Binh"));
    mvc.perform(get(BASE + "/" + id).with(as(user)))
        .andExpect(jsonPath("$.data.document.number").value("000087654321"));
    mvc.perform(put(BASE + "/" + id).with(as(user)).contentType("application/json").content("""
        {"title":"MR","givenNames":"Van Binh","surname":"Nguyen","dateOfBirth":"1990-01-02","gender":"MALE",
         "document":{"documentType":"PASSPORT","number":"a1234567","issuingCountryCode":"VN",
           "nationalityCountryCode":"VN","expiresOn":"2035-01-01"}}
        """))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.document.number").value("A1234567"));
    mvc.perform(delete(BASE + "/" + id).with(as(user))).andExpect(status().isNoContent());
    mvc.perform(get(BASE + "/" + id).with(as(user))).andExpect(status().isNotFound());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold WHERE user_id=?", Integer.class, user));
  }

  @Test void requiresAuthenticationAndEnforcesOwnershipForEveryOperation() throws Exception {
    mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mvc.perform(post(BASE).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
    String id = createProfile();
    mvc.perform(get(BASE).with(as(otherUser))).andExpect(jsonPath("$.data.length()").value(0));
    mvc.perform(get(BASE + "/" + id).with(as(otherUser))).andExpect(status().isNotFound());
    mvc.perform(put(BASE + "/" + id).with(as(otherUser)).contentType("application/json").content(BODY))
        .andExpect(status().isNotFound());
    mvc.perform(delete(BASE + "/" + id).with(as(otherUser))).andExpect(status().isNotFound());
    mvc.perform(get(BASE + "/" + id).with(as(user))).andExpect(status().isOk());
  }

  @Test void rejectsInvalidProfilesAndAllowsChildBirthDatesWithoutTripSpecificPassengerType() throws Exception {
    for (String body : new String[]{"{}", "{", BODY.replace("1990-01-02", "2099-01-01"),
        BODY.replace("000012345678", "123"), BODY.replace("\"VN\"", "\"ZZ\""),
        BODY.replace("PERSONAL_ID", "PASSPORT")}) {
      mvc.perform(post(BASE).with(as(user)).contentType("application/json").content(body))
          .andExpect(status().is(422));
    }
    mvc.perform(post(BASE).with(as(user)).contentType("application/json")
        .content(BODY.replace("\"title\":\"MR\",", "").replace("1990-01-02", "2029-01-02")))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.data.title").isEmpty());
  }
}
