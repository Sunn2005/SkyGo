import type { ReactNode } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, View } from "react-native";
import { Stack } from "expo-router/stack";
import { router } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Icon, Label, Row, Tap } from "./ui";
import { colors as c, fonts, space } from "@/shared/theme";

export function Screen({
  children,
  title,
  step,
  footer,
  right,
  home,
  onBack,
}: {
  children: ReactNode;
  title: string;
  step?: number;
  footer?: ReactNode;
  right?: ReactNode;
  home?: boolean;
  onBack?: () => void;
}) {
  const inset = useSafeAreaInsets();
  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: c.white }}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
      keyboardVerticalOffset={inset.top + 56}
    >
      <Stack.Screen
        options={{
          title,
          headerTitleAlign: "left",
          headerShadowVisible: false,
          headerStyle: { backgroundColor: c.white },
          headerTintColor: c.ink,
          headerTitleStyle: { fontFamily: fonts.semibold, fontSize: 18 },
          headerBackButtonDisplayMode: "minimal",
          headerLeft: home
            ? () => (
                <View
                  style={{
                    marginLeft: Platform.OS === "web" ? space.xl : space.sm,
                    marginRight: space.lg,
                  }}
                >
                  <Icon name="home-design-imgPlane" size={22} />
                </View>
              )
            : () => (
                <Tap
                  label="Quay lại"
                  onPress={
                    onBack ??
                    (() =>
                      router.canGoBack() ? router.back() : router.replace("/"))
                  }
                  style={{
                    marginLeft: Platform.OS === "web" ? space.xl : space.sm,
                    paddingRight: space.lg,
                  }}
                >
                  <Icon name="6-6734-imgArrowLeft" size={22} />
                </Tap>
              ),
          headerRight: () => (
            <View
              style={{
                paddingRight: Platform.OS === "web" ? space.xl : space.sm,
              }}
            >
              {right ?? (
                <Tap
                  label="Trợ giúp đặt vé"
                  onPress={() => router.push("/help")}
                >
                  <Icon name="6-6816-imgCircleHelp" size={21} />
                </Tap>
              )}
            </View>
          ),
        }}
      />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          paddingHorizontal: space.xl,
          paddingTop: step === undefined ? space.md : 0,
          paddingBottom: space.xl,
          gap: 14,
          flexGrow: 1,
        }}
      >
        {step !== undefined && (
          <Row style={{ gap: space.sm, paddingBottom: space.md }}>
            {["Hành khách", "Hành lý", "Thanh toán"].map(
              (name, i) => (
                <View
                  key={name}
                  style={{ flex: 1, gap: 7, paddingBottom: space.sm }}
                >
                  <View
                    style={{
                      height: 3,
                      borderRadius: 2,
                      backgroundColor: i <= step ? c.primary : c.line,
                    }}
                  />
                  <Label
                    size={10}
                    color={i <= step ? c.primary : c.muted}
                    weight={i === step ? "bold" : "regular"}
                  >
                    {name}
                  </Label>
                </View>
              ),
            )}
          </Row>
        )}
        {children}
      </ScrollView>
      {footer && (
        <View
          style={{
            paddingHorizontal: space.xl,
            paddingTop: 14,
            paddingBottom: Math.max(inset.bottom, 12),
            borderTopWidth: 1,
            borderTopColor: c.line,
            backgroundColor: c.white,
          }}
        >
          {footer}
        </View>
      )}
    </KeyboardAvoidingView>
  );
}
