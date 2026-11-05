package com.skygo.backend.booking.job;

import com.skygo.backend.booking.service.SeatHoldExpiryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SeatHoldExpiryJob {
  private final SeatHoldExpiryService expiry;
  public SeatHoldExpiryJob(SeatHoldExpiryService expiry) { this.expiry = expiry; }
  @Scheduled(fixedDelayString = "${skygo.seat-hold.cleanup-delay-ms:30000}",
      initialDelayString = "${skygo.seat-hold.cleanup-delay-ms:30000}")
  public void expire() { expiry.expireBatch(); }
}
