import { useState } from "react";
import type { TextInputProps } from "react-native";
import { Image } from "expo-image";
import { Field } from "@/shared/components/fields";
import { Tap } from "@/shared/components/ui";

const eye = require("../../../../assets/icons/eye.svg");
const eyeOff = require("../../../../assets/icons/eye-off.svg");

export function PasswordField(
  props: TextInputProps & { label: string; error?: string },
) {
  const [visible, setVisible] = useState(false);
  return (
    <Field
      {...props}
      secureTextEntry={!visible}
      autoCapitalize="none"
      autoCorrect={false}
      trailing={
        <Tap
          label={`${visible ? "Ẩn" : "Hiện"} ${props.label.toLowerCase()}`}
          disabled={props.editable === false}
          onPress={() => setVisible((value) => !value)}
          style={{ width: 44, alignItems: "center" }}
        >
          <Image
            source={visible ? eye : eyeOff}
            style={{ width: 22, height: 22 }}
            contentFit="contain"
            accessibilityElementsHidden
            importantForAccessibility="no"
          />
        </Tap>
      }
    />
  );
}
