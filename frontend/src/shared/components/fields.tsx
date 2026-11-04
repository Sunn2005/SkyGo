import { TextInput, View, type TextInputProps } from "react-native";
import { Icon, Label, Row, Tap } from "./ui";
import { colors as c, fonts, radius, space } from "@/shared/theme";

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
