import { View } from "react-native";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { useBooking } from "../context/booking-context";
import { fullName } from "../utils/traveller";
import { EXTRA_BAG_PRICE, includedBagWeight } from "../services/booking-rules";
import { money } from "@/shared/utils/format";
import { ActionBar } from "../components/action-bar";
import { Card, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { TripSummary } from "../components/trip-summary";
import { colors as c } from "@/shared/theme";
export default function BaggageScreen() {
  const b = useBooking();
  const { returnTo } = useLocalSearchParams<{ returnTo?: string }>();
  if (b.selected.length !== b.legs.length) return <Redirect href="/" />;
  return (
    <Screen
      title="Hành lý"
      step={1}
      footer={
        <ActionBar
          title={returnTo ? "Lưu hành lý" : "Kiểm tra & thanh toán"}
          price={b.total}
          onPress={() => (returnTo ? router.back() : router.push("/payment"))}
        />
      }
    >
      <TripSummary />
      <Card>
        <Label weight="semibold">Hành lý đã bao gồm</Label>
        <Label>Đồ cá nhân + xách tay: tổng 10 kg</Label>
        <Label>
          1 kiện ký gửi tối đa {includedBagWeight(b.search.cabin)} kg / người /
          chặng
        </Label>
      </Card>
      {b.legs.map((leg, l) => (
        <View key={l} style={{ gap: 14 }}>
          <Label size={20} weight="semibold">
            {l === 0 ? "Lượt đi" : "Lượt về"} · {leg.from} → {leg.to}
          </Label>
          {b.travellers.map((p, i) => (
            <View key={i} style={{ gap: 8 }}>
              <Label weight="semibold">
                {fullName(p) || `Hành khách ${i + 1}`}
              </Label>
              {[0, 1].map((n) => (
                <Tap
                  key={n}
                  selected={(p.extraBags[l] ?? 0) === n}
                  onPress={() =>
                    b.updateTraveller(i, {
                      extraBags: { ...p.extraBags, [l]: n },
                    })
                  }
                >
                  <Card
                    selected={(p.extraBags[l] ?? 0) === n}
                    style={{
                      backgroundColor:
                        (p.extraBags[l] ?? 0) === n ? c.tint : c.white,
                    }}
                  >
                    <Row>
                      <Label style={{ flex: 1 }}>
                        {n
                          ? "Mua thêm 1 kiện · tối đa 23 kg"
                          : "Dùng hành lý đã gồm"}
                      </Label>
                      <Label weight="semibold">
                        {n ? `+${money(EXTRA_BAG_PRICE)}` : "0đ"}
                      </Label>
                    </Row>
                  </Card>
                </Tap>
              ))}
            </View>
          ))}
        </View>
      ))}
      <Notice>
        Mỗi người được mua thêm tối đa 1 kiện 23 kg cho từng chặng. Không sử
        dụng kiện miễn phí không làm giảm giá vé.
      </Notice>
    </Screen>
  );
}
