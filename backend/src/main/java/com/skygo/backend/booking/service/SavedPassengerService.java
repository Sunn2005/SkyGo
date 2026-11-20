package com.skygo.backend.booking.service;

import com.skygo.backend.booking.contract.*;
import com.skygo.backend.booking.contract.PassengerRequest.*;
import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.repository.SavedPassengerRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedPassengerService {
  private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());
  private final SavedPassengerRepository repository;
  private final BookingDocumentEncryptionService encryption;
  private final Clock clock;
  public SavedPassengerService(SavedPassengerRepository repository, BookingDocumentEncryptionService encryption, Clock clock) {
    this.repository = repository; this.encryption = encryption; this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<SavedPassengerResponse.Summary> list(long user) { return repository.list(user); }

  @Transactional(readOnly = true)
  public SavedPassengerResponse get(long user, String id) {
    var p = repository.find(user, id).orElseThrow(SavedPassengerService::missing);
    String number = encryption.decryptSavedPassenger(p.ciphertext(), user, id);
    return new SavedPassengerResponse(id, p.title(), p.givenNames(), p.surname(), p.dateOfBirth(), p.gender(),
        new Document(p.documentType(), number, p.issuingCountry(), p.nationality(), p.expiresOn()));
  }

  @Transactional
  public SavedPassengerResponse create(long user, SavedPassengerRequest input) {
    var p = validate(input);
    repository.lockUser(user);
    if (repository.count(user) >= 50)
      throw new BookingException(409, "SAVED_PASSENGER_LIMIT", "Bạn có thể lưu tối đa 50 hành khách.");
    String id = UUID.randomUUID().toString();
    repository.create(user, id, p, encryption.encryptSavedPassenger(p.document().number(), user, id), clock.instant());
    return response(id, p);
  }

  @Transactional
  public SavedPassengerResponse update(long user, String id, SavedPassengerRequest input) {
    if (repository.find(user, id).isEmpty()) throw missing();
    var p = validate(input);
    if (repository.update(user, id, p, encryption.encryptSavedPassenger(p.document().number(), user, id), clock.instant()) == 0)
      throw missing();
    return response(id, p);
  }

  @Transactional
  public void delete(long user, String id) {
    if (repository.delete(user, id) == 0) throw missing();
  }

  private SavedPassengerResponse response(String id, SavedPassengerRequest p) {
    return new SavedPassengerResponse(id, p.title(), p.givenNames(), p.surname(), p.dateOfBirth(), p.gender(), p.document());
  }
  private SavedPassengerRequest validate(SavedPassengerRequest p) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    if (p.dateOfBirth().isAfter(today) || p.dateOfBirth().isBefore(LocalDate.of(1900, 1, 1)))
      throw BookingException.invalid("dateOfBirth", "Ngày sinh phải từ năm 1900 đến hôm nay.");
    var d = p.document();
    String number = d.number().strip().toUpperCase(Locale.ROOT);
    if (d.documentType() == DocumentType.PERSONAL_ID && !number.matches("[0-9]{12}"))
      throw BookingException.invalid("document.number", "CCCD cần đúng 12 chữ số.");
    if (d.documentType() == DocumentType.PASSPORT && !number.matches("[A-Z0-9]{6,12}"))
      throw BookingException.invalid("document.number", "Hộ chiếu cần 6–12 chữ hoặc số.");
    if (!COUNTRIES.contains(d.issuingCountryCode()))
      throw BookingException.invalid("document.issuingCountryCode", "Mã quốc gia cấp không hợp lệ.");
    if (d.documentType() == DocumentType.PASSPORT) {
      if (!COUNTRIES.contains(d.nationalityCountryCode() == null ? "" : d.nationalityCountryCode()))
        throw BookingException.invalid("document.nationalityCountryCode", "Mã quốc tịch không hợp lệ.");
      if (d.expiresOn() == null)
        throw BookingException.invalid("document.expiresOn", "Vui lòng nhập ngày hết hạn hộ chiếu.");
    } else if (d.nationalityCountryCode() != null || d.expiresOn() != null)
      throw BookingException.invalid("document", "CCCD không dùng trường quốc tịch hoặc hạn hộ chiếu.");
    return new SavedPassengerRequest(p.title(), p.givenNames().strip(), p.surname().strip(), p.dateOfBirth(), p.gender(),
        new Document(d.documentType(), number, d.issuingCountryCode(), d.nationalityCountryCode(), d.expiresOn()));
  }
  private static BookingException missing() {
    return new BookingException(404, "SAVED_PASSENGER_NOT_FOUND", "Không tìm thấy hành khách đã lưu.");
  }
}
