import { useState } from "react";
import { Keyboard, Modal, ScrollView, View } from "react-native";
import { Field } from "./fields";
import { Button, Label, Row, Tap } from "./ui";
import {
  FlightCalendar,
  localDateISO,
} from "@/modules/flights/components/flight-calendar";
import { validDate } from "@/shared/utils/date";
import { colors as c } from "@/shared/theme";

export function DateField({
  label,
  value,
  onChangeText,
  error,
  half,
  pastOnly = false,
}: {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  error?: string;
  half?: boolean;
  pastOnly?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const today = localDateISO(new Date());
  const min = pastOnly ? "1900-01-01" : today;
  const max = pastOnly ? today : "2099-12-31";
  const show = () => {
    Keyboard.dismiss();
    const parts = value.split("/");
    const iso = `${parts[2]}-${parts[1]}-${parts[0]}`;
    setDraft(validDate(iso) && iso >= min && iso <= max ? iso : today);
    setOpen(true);
  };
  return (
    <>
      <Field
        half={half}
        label={label}
        value={value}
        onChangeText={onChangeText}
        error={error}
        keyboardType="numbers-and-punctuation"
        maxLength={10}
        trailing={
          <Tap
            label={`Mở lịch ${label}`}
            onPress={show}
            style={{ width: 44, alignItems: "center" }}
          >
            <Label size={20}>📅</Label>
          </Tap>
        }
      />
      <Modal
        transparent
        visible={open}
        animationType="fade"
        onRequestClose={() => setOpen(false)}
      >
        <View
          style={{
            flex: 1,
            backgroundColor: "#102A4380",
            justifyContent: "center",
            padding: 20,
          }}
        >
          <View
            accessibilityViewIsModal
            style={{
              backgroundColor: c.white,
              borderRadius: 16,
              padding: 16,
              gap: 12,
              maxHeight: "90%",
              width: "100%",
              maxWidth: 420,
              alignSelf: "center",
            }}
          >
            <Row style={{ justifyContent: "space-between" }}>
              <Label weight="bold" size={18}>
                {pastOnly ? "Chọn ngày sinh" : "Chọn ngày hết hạn"}
              </Label>
              <Tap label="Đóng lịch" onPress={() => setOpen(false)}>
                <Label size={22}>×</Label>
              </Tap>
            </Row>
            <ScrollView>
              <FlightCalendar
                key={open ? "open" : "closed"}
                value={draft}
                minDate={min}
                maxDate={max}
                quickYear
                onChange={setDraft}
              />
            </ScrollView>
            <Button
              title="Chọn ngày này"
              onPress={() => {
                onChangeText(
                  `${draft.slice(8, 10)}/${draft.slice(5, 7)}/${draft.slice(0, 4)}`,
                );
                setOpen(false);
              }}
            />
          </View>
        </View>
      </Modal>
    </>
  );
}
