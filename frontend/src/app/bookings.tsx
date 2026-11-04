import Screen from "@/modules/tickets/screens/bookings-screen";
import { BookingAuthGate } from "@/modules/account/components/booking-auth-gate";
export default function Route() {
  return (
    <BookingAuthGate returnTo="bookings">
      <Screen />
    </BookingAuthGate>
  );
}
