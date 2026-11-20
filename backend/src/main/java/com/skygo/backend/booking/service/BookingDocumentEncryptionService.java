package com.skygo.backend.booking.service;

import com.skygo.backend.booking.exception.BookingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BookingDocumentEncryptionService {
  private final String encodedKey;
  private final String keyId;
  private final SecureRandom random = new SecureRandom();

  public BookingDocumentEncryptionService(
      @Value("${skygo.booking.document-encryption-key:}") String encodedKey,
      @Value("${skygo.booking.document-key-id:v1}") String keyId) {
    this.encodedKey = encodedKey;
    this.keyId = keyId;
  }

  private SecretKeySpec key() {
    try {
      byte[] bytes = Base64.getDecoder().decode(encodedKey);
      if (bytes.length != 32 || !keyId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException();
      return new SecretKeySpec(bytes, "AES");
    } catch (IllegalArgumentException ex) {
      throw new BookingException(503, "DOCUMENT_ENCRYPTION_UNAVAILABLE", "Khóa mã hóa giấy tờ chưa hợp lệ.");
    }
  }

  private byte[] aad(String bookingId, String passengerId) {
    return ("skygo-booking-document|1|" + keyId + "|" + bookingId + "|" + passengerId)
        .getBytes(StandardCharsets.UTF_8);
  }

  public byte[] encrypt(String number, String bookingId, String passengerId) {
    return encrypt(number, aad(bookingId, passengerId));
  }

  // Separate AAD binds reusable profiles to their account and ID, outside booking snapshots.
  private byte[] savedAad(long user, String id) {
    return ("skygo-saved-passenger-document|1|" + keyId + "|" + user + "|" + id).getBytes(StandardCharsets.UTF_8);
  }
  public byte[] encryptSavedPassenger(String number, long user, String id) {
    return encrypt(number, savedAad(user, id));
  }
  public String decryptSavedPassenger(byte[] envelope, long user, String id) {
    return decrypt(envelope, savedAad(user, id));
  }

  private byte[] encrypt(String number, byte[] associatedData) {
    var secret = key();
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(128, nonce));
      cipher.updateAAD(associatedData);
      byte[] encrypted = cipher.doFinal(number.getBytes(StandardCharsets.UTF_8));
      byte[] id = keyId.getBytes(StandardCharsets.UTF_8);
      return ByteBuffer.allocate(2 + id.length + nonce.length + encrypted.length)
          .put((byte) 1).put((byte) id.length).put(id).put(nonce).put(encrypted).array();
    } catch (GeneralSecurityException ex) {
      throw new BookingException(503, "DOCUMENT_ENCRYPTION_UNAVAILABLE", "Không thể mã hóa số giấy tờ.");
    }
  }

  public String decrypt(byte[] envelope, String bookingId, String passengerId) {
    return decrypt(envelope, aad(bookingId, passengerId));
  }

  private String decrypt(byte[] envelope, byte[] associatedData) {
    var secret = key();
    try {
      var data = ByteBuffer.wrap(envelope);
      if (Byte.toUnsignedInt(data.get()) != 1) throw new GeneralSecurityException();
      int length = Byte.toUnsignedInt(data.get());
      if (length < 1 || length > 64 || data.remaining() < length + 12 + 16) throw new GeneralSecurityException();
      byte[] id = new byte[length];
      data.get(id);
      if (!keyId.equals(new String(id, StandardCharsets.UTF_8))) throw new GeneralSecurityException();
      byte[] nonce = new byte[12];
      data.get(nonce);
      byte[] ciphertext = new byte[data.remaining()];
      data.get(ciphertext);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(128, nonce));
      cipher.updateAAD(associatedData);
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | RuntimeException ex) {
      throw new BookingException(409, "BOOKING_DOCUMENT_INVALID", "Dữ liệu giấy tờ không hợp lệ.");
    }
  }
}
