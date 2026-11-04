import Screen from "@/modules/payment/screens/payos-screen";
import { BookingAuthGate } from "@/modules/account/components/booking-auth-gate";
export default function Route() {
  return (
    <BookingAuthGate returnTo="payos">
      <Screen />
    </BookingAuthGate>
  );
}
