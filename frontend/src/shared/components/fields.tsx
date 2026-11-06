import type { ReactNode } from "react";
import { TextInput, View, type TextInputProps } from "react-native";
import { Icon, Label, Row, Tap } from "./ui";
import { colors as c, fonts, radius, space } from "@/shared/theme";

export function Field({
  label,
  error,
  style,
  half,
  trailing,
  ...props
}: TextInputProps & {
  label: string;
  error?: string;
  half?: boolean;
  trailing?: ReactNode;
  style?: TextInputProps["style"];
}) {
  return (
    <View
      style={{
        flex: half ? 1 : undefined,
        width: half ? undefined : "100%",
        minWidth: 0,
        gap: 6,
      }}
    >
      <Label size={12} muted>
        {label}
      </Label>
      <View style={{ position: "relative" }}>
        <TextInput
          {...props}
          underlineColorAndroid="transparent"
          accessibilityLabel={label}
          placeholderTextColor={c.muted}
          style={[
            {
              minWidth: 0,
              width: "100%",
              minHeight: 46,
              paddingHorizontal: 14,
              paddingVertical: 10,
              borderWidth: 1,
              borderColor: error ? c.danger : c.line,
              backgroundColor: c.surface,
              borderRadius: radius.sm,
              color: c.ink,
              fontFamily: fonts.regular,
              fontSize: 15,
            },
            trailing ? { paddingRight: 52 } : undefined,
            style,
          ]}
        />
        {trailing && (
          <View
            style={{
              position: "absolute",
              right: 4,
              top: 0,
              bottom: 0,
              justifyContent: "center",
            }}
          >
            {trailing}
          </View>
        )}
      </View>
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
    <View
      style={{
        flex: half ? 1 : undefined,
        width: half ? undefined : "100%",
        minWidth: 0,
        gap: 6,
      }}
    >
      <Label size={12} muted>
        {label}
      </Label>
      <Tap
        label={`${label}: ${value}`}
        onPress={onPress}
        style={{
          minHeight: 46,
          borderWidth: 1,
          borderColor: c.line,
          backgroundColor: c.surface,
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
