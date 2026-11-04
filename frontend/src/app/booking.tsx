import Screen from "@/modules/tickets/screens/booking-screen";
import { BookingAuthGate } from "@/modules/account/components/booking-auth-gate";
export default function Route() {
  return (
    <BookingAuthGate returnTo="booking">
      <Screen />
    </BookingAuthGate>
  );
}
