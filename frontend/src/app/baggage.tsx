import Screen from "@/modules/booking/screens/baggage-screen";
import { BookingAuthGate } from "@/modules/account/components/booking-auth-gate";
export default function Route() {
  return (
    <BookingAuthGate returnTo="baggage">
      <Screen />
    </BookingAuthGate>
  );
}
