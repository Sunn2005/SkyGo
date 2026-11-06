import type { ReactNode } from "react";
import {
  ActivityIndicator,
  Pressable,
  Text,
  View,
  type StyleProp,
  type TextProps,
  type ViewStyle,
} from "react-native";
import { Image } from "expo-image";
import { assets } from "@/shared/assets";
import { colors as c, fonts, radius, space } from "@/shared/theme";

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
          flexShrink: 1,
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
      style={{ width: width ?? size, height: size, flexShrink: 0 }}
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
  fill = false,
}: {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  secondary?: boolean;
  fill?: boolean;
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
        flex: fill ? 1 : undefined,
        minWidth: 0,
        minHeight: 54,
        padding: space.md,
        borderRadius: radius.md,
        borderCurve: "continuous",
        justifyContent: "center",
        alignItems: "center",
        backgroundColor: c.primary,
        opacity: disabled ? 0.45 : pressed ? 0.75 : 1,
      })}
    >
      {loading ? (
        <ActivityIndicator color={c.white} />
      ) : (
        <Label
          size={16}
          weight="semibold"
          color={c.white}
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
        {
          flexDirection: "row",
          alignItems: "center",
          gap: space.md,
          minWidth: 0,
        },
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
