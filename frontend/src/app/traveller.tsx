import Screen from "@/modules/booking/screens/traveller-screen";
import { BookingAuthGate } from "@/modules/account/components/booking-auth-gate";
export default function Route() {
  return (
    <BookingAuthGate returnTo="traveller">
      <Screen />
    </BookingAuthGate>
  );
}
