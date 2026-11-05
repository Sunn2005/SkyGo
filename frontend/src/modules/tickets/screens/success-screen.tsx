import { View } from "react-native";
import { Image } from "expo-image";
import { Redirect, router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { airport } from "@/modules/flights/data/airports";
import { assets } from "@/shared/assets";
import { Ticket } from "@/modules/tickets/components/ticket";
import { ActionBar } from "@/modules/booking/components/action-bar";
import { Icon, Label, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c, radius, space } from "@/shared/theme";

export default function SuccessScreen() {
  const { receipt } = useBooking();
  if (!receipt) return <Redirect href="/" />;
  return (
    <Screen
      design
      title="SkyGo"
      onBack={() => router.replace("/")}
      footer={
        <ActionBar
          title="Xem đặt chỗ"
          onPress={() => router.push("/booking")}
        />
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
            {airport(receipt.search.to).city} đang chờ bạn!
          </Label>
          <Label size={14} muted style={{ textAlign: "center" }}>
            Đặt vé thành công
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
