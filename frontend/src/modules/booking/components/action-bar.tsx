import { View } from "react-native";
import { Button, Label, Row } from "@/shared/components/ui";
import { money } from "@/shared/utils/format";
import { useBooking } from "../context/booking-context";

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
          <Label selectable size={22} weight="bold">
            {money(price)}
          </Label>
          <Label size={10} muted>
            Tổng · VND · {search.count} người
          </Label>
        </View>
      )}
      <Button
        fill
        title={title}
        onPress={onPress}
        loading={loading}
        disabled={disabled}
      />
    </Row>
  );
}
