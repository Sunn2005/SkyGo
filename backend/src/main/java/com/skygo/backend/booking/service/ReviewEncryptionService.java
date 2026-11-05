package com.skygo.backend.booking.service;

import com.skygo.backend.booking.exception.BookingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReviewEncryptionService {
  private final String encodedKey,keyId;
  private final SecureRandom random=new SecureRandom();
  public ReviewEncryptionService(@Value("${skygo.review.encryption-key:}") String encodedKey,
      @Value("${skygo.review.key-id:v1}") String keyId) {
    this.encodedKey=encodedKey; this.keyId=keyId;
  }
  public record Context(String reviewId,long userId,long sessionId,long seatHoldId,int schemaVersion) {}
  private SecretKeySpec key() {
    try {
      byte[] bytes=Base64.getDecoder().decode(encodedKey);
      if(bytes.length!=32 || !keyId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException();
      return new SecretKeySpec(bytes,"AES");
    } catch(IllegalArgumentException ex) {
      throw new BookingException(503,"REVIEW_ENCRYPTION_UNAVAILABLE","Backend chưa được cấu hình khóa mã hóa review hợp lệ.");
    }
  }
  private byte[] aad(Context c) {
    return ("skygo-review|1|"+keyId+"|"+c.reviewId()+"|"+c.userId()+"|"+c.sessionId()+"|"+c.seatHoldId()+"|"+c.schemaVersion())
        .getBytes(StandardCharsets.UTF_8);
  }
  public byte[] encrypt(byte[] plaintext,Context context) {
    var secret=key();
    try {
      byte[] nonce=new byte[12]; random.nextBytes(nonce);
      var cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE,secret,new GCMParameterSpec(128,nonce));
      cipher.updateAAD(aad(context));
      byte[] ciphertext=cipher.doFinal(plaintext);
      byte[] id=keyId.getBytes(StandardCharsets.UTF_8);
      return ByteBuffer.allocate(2+id.length+nonce.length+ciphertext.length)
          .put((byte)1).put((byte)id.length).put(id).put(nonce).put(ciphertext).array();
    } catch(GeneralSecurityException ex) {
      throw new BookingException(503,"REVIEW_ENCRYPTION_UNAVAILABLE","Không thể mã hóa review.");
    }
  }
  // Caller must verify review ownership, session and expiry before decrypting stored snapshots.
  public byte[] decrypt(byte[] envelope,Context context) {
    var secret=key();
    try {
      var data=ByteBuffer.wrap(envelope);
      if(data.get()!=1) throw new GeneralSecurityException();
      int length=Byte.toUnsignedInt(data.get());
      if(length<1 || length>64 || data.remaining()<length+12+16) throw new GeneralSecurityException();
      byte[] id=new byte[length]; data.get(id);
      if(!keyId.equals(new String(id,StandardCharsets.UTF_8))) throw new GeneralSecurityException();
      byte[] nonce=new byte[12]; data.get(nonce);
      byte[] ciphertext=new byte[data.remaining()]; data.get(ciphertext);
      var cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,secret,new GCMParameterSpec(128,nonce));
      cipher.updateAAD(aad(context));
      return cipher.doFinal(ciphertext);
    } catch(GeneralSecurityException|RuntimeException ex) {
      throw new BookingException(409,"REVIEW_INVALID","Dữ liệu review không hợp lệ hoặc không thể xác thực.");
    }
  }
}
