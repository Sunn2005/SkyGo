import Screen from "@/modules/tickets/screens/success-screen";
import { BookingAuthGate } from "@/modules/account/components/booking-auth-gate";
export default function Route() {
  return (
    <BookingAuthGate returnTo="success">
      <Screen />
    </BookingAuthGate>
  );
}
