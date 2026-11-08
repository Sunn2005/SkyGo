package com.skygo.backend.booking.service;

import com.skygo.backend.booking.exception.BookingException;
import com.skygo.backend.booking.repository.PaymentAttemptRepository.Attempt;
import com.skygo.backend.booking.repository.PaymentAttemptRepository;
import com.skygo.backend.booking.repository.PaymentWebhookRepository;
import com.skygo.backend.booking.service.payos.PayosGateway;
import com.skygo.backend.booking.service.payos.PayosWebhookVerifier.Transfer;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ExpiredPaymentVerifier {
  private final PayosGateway payos;
  private final PaymentAttemptRepository attempts;
  private final PaymentWebhookRepository payments;
  public ExpiredPaymentVerifier(PayosGateway payos,PaymentAttemptRepository attempts,PaymentWebhookRepository payments) {
    this.payos=payos;this.attempts=attempts;this.payments=payments;
  }
  public record Evidence(Attempt expected, PayosGateway.Status status) {
    public boolean hasMoney() {
      return status.amountPaid()>0 || !status.transactions().isEmpty() || status.status().equals("PAID");
    }
  }

  // Never called while holding the booking/inventory transaction open.
  public Map<Long,Evidence> verify(List<Attempt> attempts) {
    var result=new HashMap<Long,Evidence>();
    try {
      for(var a:attempts) {
        var status=checked(a,payos.lookup(a.orderCode()));
        if (!money(status) && status.status().equals("PENDING")) {
          // The cancel response can be lost after PayOS applied the cancellation.
          try { checked(a,payos.cancel(a.orderCode())); }
          catch(PayosGateway.Failure ignored) { /* Confirm the final state below. */ }
          status=checked(a,payos.lookup(a.orderCode()));
        }
        if (!money(status) && !Set.of("CANCELLED","EXPIRED").contains(status.status()))
          throw unverified();
        result.put(a.id(),new Evidence(a,status));
      }
      return Map.copyOf(result);
    } catch(PayosGateway.Failure ex) { throw unverified(); }
  }

  private static boolean money(PayosGateway.Status s) {
    return s.amountPaid()>0 || !s.transactions().isEmpty() || s.status().equals("PAID");
  }
  private static PayosGateway.Status checked(Attempt a, PayosGateway.Status s) {
    if (s==null || s.paymentLinkId()==null || s.paymentLinkId().isBlank()
        || s.orderCode()!=a.orderCode() || s.amount()!=a.amount() || s.amountPaid()<0
        || s.transactions()==null || s.status()==null
        || !Set.of("PENDING","PROCESSING","PAID","CANCELLED","EXPIRED").contains(s.status())
        || a.paymentLinkId()!=null && !a.paymentLinkId().equals(s.paymentLinkId()))
      throw unverified();
    return s;
  }
  public static BookingException unverified() {
    return new BookingException(409,"PAYMENT_STATUS_UNVERIFIED",
        "Chưa xác minh được link thanh toán cũ đã kết thúc và chưa nhận tiền. Vui lòng thử lại sau.");
  }
  // Caller owns account/hold/booking/payment/attempt locks. Persist provider transfers before returning a domain error.
  public boolean record(Evidence evidence) {
    var a=evidence.expected();var status=evidence.status();
    boolean money=evidence.hasMoney() || payments.hasTransfers(a.id());
    attempts.verifiedStatus(a.id(),status.paymentLinkId(),
        status.amountPaid()>=a.amount()?"PAID":evidence.hasMoney()?a.status():status.status());
    for(var t:status.transactions()) {
      var old=payments.reference(t.reference());
      if(old.isPresent()) {
        var r=old.get();
        if(r.attemptId()!=a.id() || r.amount()!=t.amount() || !r.currency().equals(t.currency())
            || !status.paymentLinkId().equals(r.paymentLinkId())) throw unverified();
      } else payments.record(a.id(),new Transfer(a.orderCode(),status.paymentLinkId(),t.reference(),
          t.amount(),t.currency(),t.transactionDateTime(),true));
    }
    if(money) {
      payments.reconcilePending(a.id(),"HOLD_RELEASE_PROVIDER_PAYMENT");
      payments.reconcilePayment(a.bookingId());
    }
    return money;
  }
}
