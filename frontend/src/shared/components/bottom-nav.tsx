import { useState } from "react";
import { View } from "react-native";
import { router, usePathname } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { HomeIcon } from "@/modules/flights/components/home-icon";
import { useAccount } from "@/modules/account/context/account-context";
import { BookingHistoryLoginDialog } from "@/modules/account/components/booking-history-login-dialog";
import { Label, Tap } from "./ui";
import { colors } from "../theme";
const tabs = [
  { title: "Trang chủ", icon: "home", path: "/" },
  { title: "Mua vé", icon: "plane", path: "/search" },
  { title: "Lịch sử", icon: "clock", path: "/bookings" },
  { title: "Hồ sơ", icon: "user", path: "/profile" },
] as const;
export function BottomNav() {
  const path = usePathname();
  const inset = useSafeAreaInsets();
  const { session } = useAccount();
  const [loginPromptPath, setLoginPromptPath] = useState<string | null>(null);
  if (!tabs.some((t) => t.path === path)) return null;
  return (
    <>
      <View
        style={{
          backgroundColor: "white",
          borderTopLeftRadius: 24,
          borderTopRightRadius: 24,
          flexDirection: "row",
          paddingHorizontal: 10,
          paddingTop: 8,
          paddingBottom: Math.max(inset.bottom, 10),
          gap: 6,
        }}
      >
        {tabs.map((tab) => {
          const active = tab.path === path;
          return (
            <Tap
              key={tab.path}
              label={tab.title}
              selected={active}
              onPress={() => {
                if (tab.path === "/bookings" && !session) {
                  setLoginPromptPath(path);
                  return;
                }
                if (!active) router.replace(tab.path);
              }}
              style={{
                flex: 1,
                minWidth: 0,
                paddingHorizontal: 2,
                alignItems: "center",
                gap: 3,
                minHeight: 62,
                borderRadius: 18,
                paddingVertical: 6,
                backgroundColor: active ? "#DCF4F4" : "transparent",
              }}
            >
              <HomeIcon
                name={tab.icon}
                color={active ? colors.primary : colors.ink}
                size={26}
              />
              <Label
                size={12}
                style={{ textAlign: "center" }}
                weight={active ? "semibold" : "regular"}
                color={active ? colors.primary : colors.ink}
              >
                {tab.title}
              </Label>
            </Tap>
          );
        })}
      </View>
      <BookingHistoryLoginDialog
        visible={loginPromptPath === path && !session}
        onCancel={() => setLoginPromptPath(null)}
        onConfirm={() => {
          setLoginPromptPath(null);
          router.push({ pathname: "/login", params: { returnTo: "bookings" } });
        }}
      />
    </>
  );
}
