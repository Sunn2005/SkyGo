import { BookingAuthGate } from "@/modules/account/components/booking-auth-gate";
import PaymentReturnScreen from "@/modules/payment/screens/payment-return-screen";

export default function Route() {
  return (
    <BookingAuthGate returnTo="payment/success">
      <PaymentReturnScreen outcome="success" />
    </BookingAuthGate>
  );
}
