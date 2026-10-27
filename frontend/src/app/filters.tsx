import { useState } from "react";
import { Host, Picker, Switch } from "@expo/ui";
import { router } from "expo-router";
import { useBooking } from "@/context/booking-context";
import { ActionBar, Label, Screen, Tap } from "@/components/ui";
import { colors as c } from "@/theme";

export default function FiltersScreen() {
  const b = useBooking();
  const [airline, setAirline] = useState(b.airline);
  const [nonstop, setNonstop] = useState(b.nonstop);
  const [morning, setMorning] = useState(b.morning);
  const [maxPrice, setMaxPrice] = useState(b.maxPrice);
  return (
    <Screen
      title="Lọc chuyến bay"
      footer={
        <ActionBar
          title="Xem kết quả"
          onPress={() => {
            b.setAirline(airline);
            b.setNonstop(nonstop);
            b.setMorning(morning);
            b.setMaxPrice(maxPrice);
            router.back();
          }}
        />
      }
    >
      <Label size={17} weight="semibold">
        Hãng hàng không
      </Label>
      <Host matchContents colorScheme="light" seedColor={c.primary}>
        <Picker selectedValue={airline} onValueChange={setAirline}>
          <Picker.Item label="Tất cả hãng" value="all" />
          <Picker.Item label="SkyHaven" value="SkyHaven" />
          <Picker.Item label="EcoWings" value="EcoWings" />
        </Picker>
      </Host>
      <Label size={17} weight="semibold">
        Giá tối đa mỗi người
      </Label>
      <Host matchContents colorScheme="light" seedColor={c.primary}>
        <Picker selectedValue={maxPrice} onValueChange={setMaxPrice}>
          {[500, 550, 1000, 2000].map((p) => (
            <Picker.Item key={p} label={`$${p} USD`} value={p} />
          ))}
        </Picker>
      </Host>
      <Host matchContents colorScheme="light" seedColor={c.primary}>
        <Switch
          label="Chỉ bay thẳng"
          value={nonstop}
          onValueChange={setNonstop}
        />
      </Host>
      <Host matchContents colorScheme="light" seedColor={c.primary}>
        <Switch
          label="Khởi hành trước 12 giờ"
          value={morning}
          onValueChange={setMorning}
        />
      </Host>
      <Tap
        onPress={() => {
          setAirline("all");
          setNonstop(false);
          setMorning(false);
          setMaxPrice(2000);
        }}
      >
        <Label color={c.primary} weight="semibold">
          Đặt lại bộ lọc
        </Label>
      </Tap>
    </Screen>
  );
}
