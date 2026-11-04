import Screen from "@/modules/payment/screens/payment-screen";
import { BookingAuthGate } from "@/modules/account/components/booking-auth-gate";
export default function Route() {
  return (
    <BookingAuthGate returnTo="payment">
      <Screen />
    </BookingAuthGate>
  );
}
