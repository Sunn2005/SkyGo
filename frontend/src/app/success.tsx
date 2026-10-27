import { View } from "react-native";
import { Image } from "expo-image";
import { Redirect, router } from "expo-router";
import { useBooking } from "@/context/booking-context";
import { airport } from "@/data/booking";
import { assets } from "@/data/assets";
import { Ticket } from "@/components/ticket";
import { ActionBar, Icon, Label, Row, Screen, Tap } from "@/components/ui";
import { colors as c, radius, space } from "@/theme";

export default function SuccessScreen() {
  const { receipt } = useBooking();
  if (!receipt) return <Redirect href="/" />;
  return (
    <Screen
      title="SkyGo"
      onBack={() => router.replace("/")}
      footer={
        <ActionBar title="Xem đặt vé" onPress={() => router.push("/booking")} />
      }
    >
      <View style={{ alignItems: "center", gap: 14 }}>
        <View
          style={{
            width: 52,
            height: 52,
            borderRadius: 26,
            backgroundColor: c.tint,
            justifyContent: "center",
            alignItems: "center",
          }}
        >
          <Icon name="6-7220-imgCheck" size={28} />
        </View>
        <View style={{ gap: 6 }}>
          <Label size={28} weight="bold" style={{ textAlign: "center" }}>
            Hẹn gặp ở {airport(receipt.search.to).city}!
          </Label>
          <Label size={14} muted style={{ textAlign: "center" }}>
            Đã xác nhận vé mẫu. Hành trình đang chờ bạn.
          </Label>
        </View>
      </View>
      <Image
        source={assets["6-7220-imgAboveTheClouds"]}
        contentFit="cover"
        style={{ width: "100%", height: 128, borderRadius: radius.lg }}
        accessibilityLabel="Cánh máy bay trên những tầng mây"
      />
      <Ticket receipt={receipt} />
      <Row style={{ gap: 9, alignItems: "flex-start" }}>
        <Icon name="6-7220-imgMailCheck" size={18} />
        <Label size={12} muted style={{ flex: 1 }}>
          Thông tin liên hệ của vé mẫu:{"\n"}
          {receipt.email}
          {"\n"}Email chưa được gửi trong bản trải nghiệm.
        </Label>
      </Row>
      <Tap
        onPress={() => router.replace("/")}
        style={{ alignItems: "center", paddingVertical: space.xs }}
      >
        <Label size={13} color={c.primary} weight="semibold">
          Về trang chủ
        </Label>
      </Tap>
    </Screen>
  );
}
