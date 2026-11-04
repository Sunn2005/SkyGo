import { useState } from "react";
import { View, type TextInputProps } from "react-native";
import { Field } from "@/shared/components/fields";
import { Label, Tap } from "@/shared/components/ui";
import { colors } from "@/shared/theme";

export function PasswordField(
  props: TextInputProps & { label: string; error?: string },
) {
  const [visible, setVisible] = useState(false);
  return (
    <View style={{ gap: 2 }}>
      <Field
        {...props}
        secureTextEntry={!visible}
        autoCapitalize="none"
        autoCorrect={false}
      />
      <Tap
        label={`${visible ? "Ẩn" : "Hiện"} ${props.label.toLowerCase()}`}
        onPress={() => setVisible((value) => !value)}
        style={{ alignSelf: "flex-end" }}
      >
        <Label size={12} color={colors.primary} weight="medium">
          {visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
        </Label>
      </Tap>
    </View>
  );
}
