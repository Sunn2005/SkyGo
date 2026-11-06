import { Stack } from "expo-router/stack";
import { StatusBar } from "expo-status-bar";
import { ActivityIndicator, Platform, View } from "react-native";
import { useFonts } from "expo-font";
import { Inter_400Regular } from "@expo-google-fonts/inter/400Regular";
import { Inter_500Medium } from "@expo-google-fonts/inter/500Medium";
import { Inter_600SemiBold } from "@expo-google-fonts/inter/600SemiBold";
import { Inter_700Bold } from "@expo-google-fonts/inter/700Bold";
import { BookingProvider } from "@/modules/booking/context/booking-context";
import {
  AccountProvider,
  useAccount,
} from "@/modules/account/context/account-context";
import type { ReactNode } from "react";
import { ReservationNotice } from "@/modules/booking/components/reservation-notice";
import { BottomNav } from "@/shared/components/bottom-nav";
import { colors } from "@/shared/theme";

export default function RootLayout() {
  const [loaded, error] = useFonts({
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
    Inter_700Bold,
  });
  if (!loaded && !error)
    return (
      <View
        style={{
          flex: 1,
          justifyContent: "center",
          alignItems: "center",
          backgroundColor: colors.background,
        }}
      >
        <ActivityIndicator
          color={colors.primary}
          accessibilityLabel="Đang mở SkyGo"
        />
      </View>
    );
  return (
    <AccountProvider>
      <BookingSession>
        <StatusBar style="dark" />
        <View
          style={{
            flex: 1,
            backgroundColor: colors.surface,
            alignItems: "center",
          }}
        >
          <View
            style={{
              flex: 1,
              width: "100%",
              maxWidth: Platform.OS === "web" ? 402 : 600,
              backgroundColor: colors.background,
            }}
          >
            <Stack
              screenOptions={{
                contentStyle: { backgroundColor: colors.background },
                headerBackButtonDisplayMode: "minimal",
              }}
            >
              <Stack.Screen name="index" options={{ title: "SkyGo" }} />
              <Stack.Screen
                name="options"
                options={{ presentation: "modal" }}
              />
              <Stack.Screen
                name="filters"
                options={{ presentation: "modal" }}
              />
              <Stack.Screen name="help" options={{ presentation: "modal" }} />
            </Stack>
            <BottomNav />
            <ReservationNotice />
          </View>
        </View>
      </BookingSession>
    </AccountProvider>
  );
}

// Keep public flight selections on guest login; isolate personal data by account.
function BookingSession({ children }: { children: ReactNode }) {
  const { session } = useAccount();
  return (
    <BookingProvider
      accountId={session?.user.phoneNumber ?? "guest"}
      accountContact={session?.user}
    >
      {children}
    </BookingProvider>
  );
}
