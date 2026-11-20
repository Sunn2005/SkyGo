import { View } from "react-native";
import {
  Panel,
  InfoRow,
  passengerKind,
} from "@/modules/booking/components/design";
import { Label } from "@/shared/components/ui";
import { colors as c } from "@/shared/theme";
import { money } from "@/shared/utils/format";
import {
  flightPrice,
  EXTRA_BAG_PRICE,
} from "@/modules/booking/services/booking-rules";
import type { Traveller } from "@/modules/booking/types";
import type { Search, Flight } from "@/modules/flights/types";

export type PassengerPrice = {
  fareSubtotal: number;
  extraBaggageAmount: number;
  subtotal: number;
};
type Item = {
  clientPassengerId: string;
  givenNames: string;
  surname: string;
  passengerType: string;
  price?: PassengerPrice;
};
export function PassengerPriceBreakdown({
  passengers,
  includesVat = false,
}: {
  passengers: Item[];
  includesVat?: boolean;
}) {
  if (!passengers.length || passengers.some((p) => !p.price)) return null;
  return (
    <Panel>
      <Label size={18} weight="bold">
        Chi phí theo hành khách
      </Label>
      {passengers.map((p, i) => (
        <View key={p.clientPassengerId} style={{ gap: 8 }}>
          {i > 0 && <View style={{ height: 1, backgroundColor: c.line }} />}
          <Label weight="semibold">
            {p.surname} {p.givenNames} ·{" "}
            {passengerKind(p.passengerType.toLowerCase())}
          </Label>
          <InfoRow
            label="Tiền vé"
            value={money(p.price!.fareSubtotal)}
          />
          <InfoRow
            label="Hành lý mua thêm"
            value={money(p.price!.extraBaggageAmount)}
          />
          <InfoRow
            label={includesVat ? "Tổng hành khách" : "Tạm tính"}
            value={money(p.price!.subtotal)}
            bold
          />
        </View>
      ))}
      <Label size={12} muted>
        {includesVat
          ? "Các khoản tiền trong mục này đã bao gồm thuế, phí."
          : "Các khoản tiền trong mục này chưa bao gồm VAT. VAT được tính chung trong tổng thanh toán."}
      </Label>
    </Panel>
  );
}

export function demoPassengerPrices(
  search: Search,
  flights: Flight[],
  passengers: Traveller[],
): Item[] {
  return passengers.map((p, i) => {
    const kind = p.kind ?? "adult";
    const fare = flights.reduce((sum, f) => {
      const price = flightPrice(f, f.cabin ?? search.cabin);
      return (
        sum +
        (kind === "adult"
          ? price
          : Math.round(price * (kind === "child" ? 0.9 : 0.1)))
      );
    }, 0);
    const baggage =
      kind === "infant"
        ? 0
        : flights.reduce(
            (sum, _, l) => sum + (p.extraBags[l] ?? 0) * EXTRA_BAG_PRICE,
            0,
          );
    return {
      clientPassengerId: `p${i + 1}`,
      surname: p.lastName,
      givenNames: p.firstName,
      passengerType: kind,
      price: {
        fareSubtotal: fare,
        extraBaggageAmount: baggage,
        subtotal: fare + baggage,
      },
    };
  });
}
