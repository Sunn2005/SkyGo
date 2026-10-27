import { View } from "react-native";
import { Host, Switch } from "@expo/ui";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { useBooking } from "@/context/booking-context";
import { fullName, money } from "@/data/booking";
import {
  ActionBar,
  Badge,
  Card,
  Icon,
  Label,
  Notice,
  Row,
  Screen,
  Tap,
  TripSummary,
} from "@/components/ui";
import { colors as c, radius, space } from "@/theme";

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
          title={returnTo ? "Lưu hành lý" : "Tiếp tục chọn chỗ"}
          price={returnTo ? b.total : b.base + b.baggage + b.protection}
          onPress={() => (returnTo ? router.back() : router.push("/seat"))}
        />
      }
    >
      <TripSummary />
      <Label size={20} weight="semibold">
        Sẵn sàng cho chuyến đi
      </Label>
      <Row
        style={{
          padding: 14,
          backgroundColor: c.surface,
          borderRadius: radius.md,
        }}
      >
        <Icon name="6-6896-imgBackpack" size={27} />
        <View style={{ flex: 1, gap: space.xs }}>
          <Label size={14} weight="semibold">
            Đồ cá nhân + xách tay
          </Label>
          <Label size={12} muted>
            Đồ để dưới ghế · Xách tay 7 kg
          </Label>
        </View>
        <Badge>Đã gồm</Badge>
      </Row>
      {b.travellers.map((p, i) => (
        <View key={i} style={{ gap: space.md }}>
          <Row style={{ justifyContent: "space-between" }}>
            <Label size={17} weight="semibold">
              Hành lý ký gửi
            </Label>
            <Label
              muted
              size={12}
              style={{ flexShrink: 1, textAlign: "right" }}
            >
              {fullName(p)}
            </Label>
          </Row>
          {[
            { n: 0, title: "Không ký gửi", note: "Chỉ mang hành lý xách tay" },
            {
              n: 1,
              title: "1 kiện · 23 kg",
              note: "Tổng 3 chiều tối đa 158 cm",
            },
            {
              n: 2,
              title: "2 kiện · 23 kg mỗi kiện",
              note: "Thêm không gian cho hành trình",
            },
          ].map((option) => (
            <Tap
              key={option.n}
              label={`${option.title}, ${money(option.n * 45)} mỗi chặng`}
              selected={p.bags === option.n}
              onPress={() => b.updateTraveller(i, { bags: option.n })}
            >
              <Card
                selected={p.bags === option.n}
                style={{
                  backgroundColor: p.bags === option.n ? c.tint : c.white,
                  padding: 15,
                  borderRadius: radius.md,
                }}
              >
                <Row>
                  <Icon
                    name={
                      p.bags === option.n
                        ? "6-6896-imgSelection1"
                        : "6-6896-imgSelection"
                    }
                    size={19}
                  />
                  <View style={{ flex: 1, gap: space.xs }}>
                    <Label size={14} weight="semibold">
                      {option.title}
                    </Label>
                    <Label size={12} muted>
                      {option.note}
                    </Label>
                  </View>
                  <Label size={21}>
                    {option.n ? "+" : ""}
                    {money(option.n * 45)}
                  </Label>
                </Row>
              </Card>
            </Tap>
          ))}
        </View>
      ))}
      <Notice icon="6-6896-imgInfo">
        Giá áp dụng cho mỗi người, mỗi chặng. Mua hành lý trước giúp tiết kiệm
        hơn so với tại sân bay.
        {b.legs.length > 1 ? ` Hành trình này có ${b.legs.length} chặng.` : ""}
      </Notice>
      <Card style={{ borderRadius: radius.md, padding: 14 }}>
        <Row>
          <Icon name="6-6896-imgShieldCheck" />
          <View style={{ flex: 1, gap: space.xs }}>
            <Label size={14} weight="semibold">
              Bảo vệ chuyến đi
            </Label>
            <Label size={12} muted>
              $29 / người · Quyền lợi mô phỏng
            </Label>
          </View>
        </Row>
        <Host matchContents colorScheme="light" seedColor={c.primary}>
          <Switch
            label="Thêm bảo vệ chuyến đi"
            value={b.insurance}
            onValueChange={b.setInsurance}
          />
        </Host>
      </Card>
    </Screen>
  );
}
