package com.skygo.backend.booking.job;

import com.skygo.backend.booking.service.BookingHoldSwitchService;
import com.skygo.backend.booking.service.BookingDeadlineService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingHoldSwitchRecoveryJob {
  private final BookingHoldSwitchService service;
  private final BookingDeadlineService deadlines;
  public BookingHoldSwitchRecoveryJob(BookingHoldSwitchService service,BookingDeadlineService deadlines) {
    this.service=service;this.deadlines=deadlines;
  }
  @Scheduled(fixedDelayString="${skygo.seat-hold.cleanup-delay-ms:30000}",
      initialDelayString="${skygo.seat-hold.cleanup-delay-ms:30000}")
  public void recover() { deadlines.cancelDue();service.recoverPending(); }
}
