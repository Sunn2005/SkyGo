import type { ReactNode } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextInputProps,
  type TextProps,
  type ViewStyle,
} from "react-native";
import { Image } from "expo-image";
import { Stack } from "expo-router/stack";
import { router } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { assets } from "@/data/assets";
import { colors as c, fonts, radius, space } from "@/theme";
import {
  airport,
  dateLabel,
  money,
  type Flight,
  type Leg,
} from "@/data/booking";
import { useBooking } from "@/context/booking-context";

export type AssetKey = keyof typeof assets;
export function Label({
  children,
  size = 15,
  weight = "regular",
  muted,
  color,
  style,
  ...props
}: TextProps & {
  size?: number;
  weight?: keyof typeof fonts;
  muted?: boolean;
  color?: string;
}) {
  return (
    <Text
      {...props}
      style={[
        {
          fontFamily: fonts[weight],
          fontSize: size,
          lineHeight: size * 1.4,
          color: color ?? (muted ? c.muted : c.ink),
        },
        style,
      ]}
    >
      {children}
    </Text>
  );
}
export function Icon({
  name,
  size = 20,
  width,
}: {
  name: AssetKey;
  size?: number;
  width?: number;
}) {
  return (
    <Image
      source={assets[name]}
      style={{ width: width ?? size, height: size }}
      contentFit="contain"
      accessibilityElementsHidden
      importantForAccessibility="no"
    />
  );
}
export function Tap({
  children,
  onPress,
  label,
  style,
  disabled,
  selected,
}: {
  children: ReactNode;
  onPress: () => void;
  label?: string;
  style?: StyleProp<ViewStyle>;
  disabled?: boolean;
  selected?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: !!disabled, selected }}
      style={({ pressed }) => [
        {
          opacity: disabled ? 0.4 : pressed ? 0.65 : 1,
          minHeight: 44,
          justifyContent: "center",
        },
        style,
      ]}
    >
      {children}
    </Pressable>
  );
}
export function Button({
  title,
  onPress,
  loading,
  disabled,
  secondary = false,
}: {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  secondary?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled || loading}
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{
        disabled: !!disabled || !!loading,
        busy: !!loading,
      }}
      style={({ pressed }) => ({
        flex: 1,
        minHeight: 54,
        padding: space.md,
        borderRadius: radius.md,
        borderCurve: "continuous",
        justifyContent: "center",
        alignItems: "center",
        backgroundColor: secondary ? c.tint : c.primary,
        opacity: disabled ? 0.45 : pressed ? 0.75 : 1,
      })}
    >
      {loading ? (
        <ActivityIndicator color={c.white} />
      ) : (
        <Label
          size={16}
          weight="semibold"
          color={secondary ? c.primary : c.white}
          style={{ textAlign: "center" }}
        >
          {title}
        </Label>
      )}
    </Pressable>
  );
}
export function Card({
  children,
  style,
  selected,
}: {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
  selected?: boolean;
}) {
  return (
    <View
      style={[
        {
          padding: space.lg,
          gap: space.md,
          borderWidth: selected ? 1.5 : 1,
          borderColor: selected ? c.primary : c.line,
          borderRadius: radius.lg,
          borderCurve: "continuous",
          backgroundColor: c.white,
        },
        style,
      ]}
    >
      {children}
    </View>
  );
}
export function Row({
  children,
  style,
}: {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <View
      style={[
        { flexDirection: "row", alignItems: "center", gap: space.md },
        style,
      ]}
    >
      {children}
    </View>
  );
}
export function Divider() {
  return <View style={{ height: 1, backgroundColor: c.line }} />;
}
export function Badge({ children }: { children: ReactNode }) {
  return (
    <View
      style={{
        backgroundColor: c.tint,
        borderRadius: 6,
        paddingHorizontal: 9,
        paddingVertical: 5,
      }}
    >
      <Label size={11} color={c.primary} weight="semibold">
        {children}
      </Label>
    </View>
  );
}
export function SummaryRow({
  label,
  value,
  bold,
}: {
  label: string;
  value: string;
  bold?: boolean;
}) {
  return (
    <Row style={{ justifyContent: "space-between" }}>
      <Label
        muted={!bold}
        weight={bold ? "semibold" : "regular"}
        style={{ flex: 1 }}
      >
        {label}
      </Label>
      <Label
        selectable
        size={bold ? 18 : 14}
        weight={bold ? "bold" : "medium"}
        style={{ flexShrink: 1, textAlign: "right" }}
      >
        {value}
      </Label>
    </Row>
  );
}
export function Notice({
  children,
  icon = "6-6734-imgInfo",
  error,
}: {
  children: ReactNode;
  icon?: AssetKey;
  error?: boolean;
}) {
  return (
    <Row
      style={{
        alignItems: "flex-start",
        backgroundColor: error ? c.dangerTint : c.surface,
        borderRadius: radius.sm,
        padding: space.md,
      }}
    >
      {!error && <Icon name={icon} size={17} />}
      <Label
        selectable
        size={12}
        color={error ? c.danger : c.muted}
        style={{ flex: 1 }}
        accessibilityRole={error ? "alert" : undefined}
      >
        {children}
      </Label>
    </Row>
  );
}
export function Field({
  label,
  error,
  style,
  half,
  ...props
}: TextInputProps & {
  label: string;
  error?: string;
  half?: boolean;
  style?: TextInputProps["style"];
}) {
  return (
    <View style={{ flex: half ? 1 : undefined, gap: 6 }}>
      <Label size={12} muted>
        {label}
      </Label>
      <TextInput
        {...props}
        underlineColorAndroid="transparent"
        accessibilityLabel={label}
        placeholderTextColor={c.muted}
        style={[
          {
            minHeight: 46,
            paddingHorizontal: 14,
            paddingVertical: 10,
            borderWidth: 1,
            borderColor: error ? c.danger : c.line,
            borderRadius: radius.sm,
            color: c.ink,
            fontFamily: fonts.regular,
            fontSize: 15,
          },
          style,
        ]}
      />
      {!!error && (
        <Label size={11} color={c.danger}>
          {error}
        </Label>
      )}
    </View>
  );
}
export function SelectField({
  label,
  value,
  onPress,
  half,
}: {
  label: string;
  value: string;
  onPress: () => void;
  half?: boolean;
}) {
  return (
    <View style={{ flex: half ? 1 : undefined, gap: 6 }}>
      <Label size={12} muted>
        {label}
      </Label>
      <Tap
        label={`${label}: ${value}`}
        onPress={onPress}
        style={{
          borderWidth: 1,
          borderColor: c.line,
          borderRadius: radius.sm,
          paddingHorizontal: 14,
          paddingVertical: 10,
        }}
      >
        <Row style={{ gap: space.sm }}>
          <Label style={{ flex: 1 }}>{value}</Label>
          <Icon name="home-design-imgChevronDown" size={16} />
        </Row>
      </Tap>
    </View>
  );
}
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
            {["Hành khách", "Hành lý", "Chỗ ngồi", "Thanh toán"].map(
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
export function ActionBar({
  title,
  onPress,
  price,
  loading,
  disabled,
}: {
  title: string;
  onPress: () => void;
  price?: number;
  loading?: boolean;
  disabled?: boolean;
}) {
  const { search } = useBooking();
  return (
    <Row style={{ gap: 14 }}>
      {price !== undefined && (
        <View style={{ maxWidth: "40%", gap: 2 }}>
          <Label selectable size={30} weight="bold">
            {money(price)}
          </Label>
          <Label size={10} muted>
            Tổng · USD · {search.count} người
          </Label>
        </View>
      )}
      <Button
        title={title}
        onPress={onPress}
        loading={loading}
        disabled={disabled}
      />
    </Row>
  );
}
export function TripSummary({ leg }: { leg?: Leg }) {
  const { legs, search } = useBooking();
  const route = leg ?? legs[0];
  return (
    <Row
      style={{
        backgroundColor: c.surface,
        padding: space.md,
        borderRadius: radius.sm,
      }}
    >
      <Icon name="6-6816-imgPlane" />
      <View style={{ flex: 1, gap: 2 }}>
        <Label size={14} weight="semibold">
          {route.from} → {route.to}
          {!leg && legs.length > 1 ? ` · ${legs.length} chặng` : ""}
        </Label>
        <Label size={12} muted>
          {dateLabel(route.date)} · {search.count} người · {search.cabin}
        </Label>
      </View>
    </Row>
  );
}
export function FlightTimes({
  flight,
  leg,
  plane = "6-6734-imgPlane1",
}: {
  flight: Flight;
  leg: Leg;
  plane?: AssetKey;
}) {
  return (
    <Row style={{ gap: space.md }}>
      <View style={{ flex: 1, gap: space.xs }}>
        <Label selectable size={26} weight="semibold">
          {flight.departure}
        </Label>
        <Label size={11} muted>
          {leg.from} · {airport(leg.from).city}
        </Label>
      </View>
      <View style={{ flex: 1.25, gap: 5, alignItems: "center" }}>
        <Label size={10} muted>
          {flight.duration}
        </Label>
        <Row style={{ gap: 5 }}>
          <View style={{ flex: 1, height: 1, backgroundColor: c.line }} />
          <Icon name={plane} size={16} />
          <View style={{ flex: 1, height: 1, backgroundColor: c.line }} />
        </Row>
        <Label size={10} color={c.primary}>
          {flight.stops ? "1 điểm dừng" : "Bay thẳng"}
        </Label>
      </View>
      <View style={{ flex: 1, gap: space.xs, alignItems: "flex-end" }}>
        <Label selectable size={26} weight="semibold">
          {flight.arrival}
        </Label>
        <Label size={11} muted>
          {leg.to} · {airport(leg.to).city}
        </Label>
      </View>
    </Row>
  );
}
