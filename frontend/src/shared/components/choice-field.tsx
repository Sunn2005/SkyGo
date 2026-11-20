import { useState } from "react";
import { Keyboard, Modal, View } from "react-native";
import { SelectField } from "./fields";
import { Button, Label, Row, Tap } from "./ui";
import { colors as c } from "@/shared/theme";

export function ChoiceField<T extends string>({
  label,
  value,
  options,
  onChange,
  half,
  getOptionLabel = (option: T) => option,
}: {
  label: string;
  value: T;
  options: readonly T[];
  onChange: (value: T) => void;
  half?: boolean;
  getOptionLabel?: (value: T) => string;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <SelectField
        half={half}
        label={label}
        value={getOptionLabel(value)}
        onPress={() => {
          Keyboard.dismiss();
          setOpen(true);
        }}
      />
      <Modal
        visible={open}
        transparent
        animationType="fade"
        onRequestClose={() => setOpen(false)}
      >
        <View
          style={{
            flex: 1,
            justifyContent: "center",
            padding: 24,
            backgroundColor: "#102A4380",
          }}
        >
          <View
            accessibilityViewIsModal
            style={{
              backgroundColor: c.white,
              borderRadius: 16,
              padding: 20,
              gap: 12,
              width: "100%",
              maxWidth: 420,
              alignSelf: "center",
            }}
          >
            <Label size={18} weight="bold">
              {label}
            </Label>
            {options.map((option) => (
              <Tap
                key={option}
                label={getOptionLabel(option)}
                selected={option === value}
                onPress={() => {
                  onChange(option);
                  setOpen(false);
                }}
                style={{
                  padding: 12,
                  borderRadius: 8,
                  backgroundColor: option === value ? c.tint : c.surface,
                }}
              >
                <Row>
                  <Label style={{ flex: 1 }}>{getOptionLabel(option)}</Label>
                  {option === value && <Label color={c.primary}>✓</Label>}
                </Row>
              </Tap>
            ))}
            <Button secondary title="Đóng" onPress={() => setOpen(false)} />
          </View>
        </View>
      </Modal>
    </>
  );
}
