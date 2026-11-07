import LiveBaggageScreen from "./live-baggage-screen";
import { useState } from "react";
import { Modal, View } from "react-native";
import { Redirect, router } from "expo-router";
import { useBooking } from "../context/booking-context";
import { fullName } from "../utils/traveller";
import { EXTRA_BAG_PRICE, includedBagWeight } from "../services/booking-rules";
import { money } from "@/shared/utils/format";
import { Button, Label, Row, Tap } from "@/shared/components/ui";
import { Panel, passengerKind } from "../components/design";
import { TripSummary } from "../components/trip-summary";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";
export default function BaggageScreen() {
  const b = useBooking();
  const [info, setInfo] = useState(false);
  if (b.isLive) return <LiveBaggageScreen key={b.review?.reviewId} />;
  if (b.selected.length !== b.legs.length) return <Redirect href="/search" />;
  return (
    <Screen
      design
      title="Hành lý"
      step={1}
      footer={
        <Row>
          <View style={{ flex: 1, gap: 3 }}>
            <Label size={22} weight="bold">
              {money(b.baggage)}
            </Label>
            <Label size={11} muted>
              Tổng phí hành lý
            </Label>
          </View>
          <Button title="Lưu hành lý" onPress={() => router.back()} />
        </Row>
      }
    >
      <TripSummary />
      {b.legs.map((leg, l) => (
        <View key={l} style={{ gap: 16 }}>
          {b.legs.length > 1 && (
            <Label size={16} weight="bold">
              {l ? "Lượt về" : "Lượt đi"} · {leg.from} → {leg.to}
            </Label>
          )}
          {b.travellers.map((p, i) => {
            const n = p.extraBags[l] ?? 0;
            const cabin = b.selected[l].cabin ?? b.search.cabin;
            return (
              <View key={i} style={{ gap: 12 }}>
                <View style={{ gap: 4 }}>
                  <Label size={20} weight="bold">
                    {fullName(p)}
                  </Label>
                  <Label size={12} muted>
                    {passengerKind(p.kind)} · {cabin}
                  </Label>
                </View>
                {p.kind === "infant" ? (
                  <Panel>
                    <Label size={13}>
                      Em bé ngồi cùng người lớn, không mua hành lý riêng. Hành
                      lý mua thêm được chọn trên người lớn đi kèm.
                    </Label>
                  </Panel>
                ) : (
                  <Panel>
                    <Label size={11} muted weight="semibold">
                      MIỄN CƯỚC
                    </Label>
                    <View style={{ height: 1, backgroundColor: c.line }} />
                    <Label size={14}>✓ Hành lý xách tay: 10 kg</Label>
                    <Label size={14}>
                      ✓ Hành lý ký gửi: 1 kiện × {includedBagWeight(cabin)} kg
                    </Label>
                    <View
                      style={{
                        height: 1,
                        backgroundColor: c.line,
                        marginVertical: 4,
                      }}
                    />
                    <Label size={17} weight="bold">
                      Mua thêm kiện ký gửi
                    </Label>
                    <Label size={12} muted>
                      Mỗi kiện tối đa 23 kg, tổng 3 chiều tối đa 158 cm
                    </Label>
                    <Row>
                      <Tap
                        label={`Bớt hành lý hành khách ${i + 1} chặng ${l + 1}`}
                        disabled={n === 0}
                        onPress={() =>
                          b.updateTraveller(i, {
                            extraBags: { ...p.extraBags, [l]: n - 1 },
                          })
                        }
                        style={{
                          width: 36,
                          borderWidth: 1,
                          borderColor: c.line,
                          borderRadius: 20,
                          alignItems: "center",
                        }}
                      >
                        <Label size={22}>−</Label>
                      </Tap>
                      <Label weight="bold">{n}</Label>
                      <Tap
                        label={`Thêm hành lý hành khách ${i + 1} chặng ${l + 1}`}
                        disabled={n === 4}
                        onPress={() =>
                          b.updateTraveller(i, {
                            extraBags: { ...p.extraBags, [l]: n + 1 },
                          })
                        }
                        style={{
                          width: 36,
                          borderWidth: 1,
                          borderColor: c.line,
                          borderRadius: 20,
                          alignItems: "center",
                        }}
                      >
                        <Label size={22}>+</Label>
                      </Tap>
                      <Label
                        size={13}
                        weight="bold"
                        style={{ flex: 1, textAlign: "right" }}
                      >
                        {money(EXTRA_BAG_PRICE)}/kiện
                      </Label>
                    </Row>
                    <Row style={{ justifyContent: "space-between" }}>
                      <Label size={12} muted>
                        Thành tiền
                      </Label>
                      <Label size={12} weight="semibold" color={c.primary}>
                        {n} kiện × {money(EXTRA_BAG_PRICE)} ={" "}
                        {money(n * EXTRA_BAG_PRICE)}
                      </Label>
                    </Row>
                    <Tap onPress={() => setInfo(true)}>
                      <Label size={12} color={c.primary} weight="semibold">
                        ⓘ 1 kiện là gì?
                      </Label>
                    </Tap>
                  </Panel>
                )}
              </View>
            );
          })}
        </View>
      ))}
      <Modal
        transparent
        visible={info}
        animationType="fade"
        onRequestClose={() => setInfo(false)}
      >
        <View
          style={{
            flex: 1,
            backgroundColor: "#102A4380",
            justifyContent: "center",
            padding: 24,
          }}
        >
          <Panel>
            <Label size={20} weight="bold">
              1 kiện là gì?
            </Label>
            <Label>
              Một túi hoặc vali được đóng gói riêng, tối đa 23 kg và tổng ba
              chiều không quá 158 cm. Mỗi khách có ghế riêng được mua thêm tối
              đa 4 kiện mỗi chiều, ngoài kiện miễn cước.
            </Label>
            <Button title="Đã hiểu" onPress={() => setInfo(false)} />
          </Panel>
        </View>
      </Modal>
    </Screen>
  );
}
