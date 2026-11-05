import { useResponsiveLayout } from "@/shared/hooks/use-responsive-layout";
import { View } from "react-native";
import { Button, Label, Row } from "@/shared/components/ui";
import { money } from "@/shared/utils/format";

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
  const { compact } = useResponsiveLayout();
  const stacked = compact && price !== undefined;
  return (
    <Row
      style={{
        gap: 12,
        flexDirection: stacked ? "column" : "row",
        alignItems: "stretch",
      }}
    >
      {price !== undefined && (
        <View
          style={{ width: stacked ? "100%" : "43%", flexShrink: 0, gap: 2 }}
        >
          <Label
            selectable
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.7}
            size={20}
            weight="bold"
          >
            {money(price)}
          </Label>
          <Label size={10} muted>
            Tổng thanh toán · VNĐ
          </Label>
        </View>
      )}
      <Button
        fill={!stacked}
        title={title}
        onPress={onPress}
        loading={loading}
        disabled={disabled}
      />
    </Row>
  );
}
