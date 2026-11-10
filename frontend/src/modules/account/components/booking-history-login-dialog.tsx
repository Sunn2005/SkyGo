import { Modal, View } from "react-native";
import { HomeIcon } from "@/modules/flights/components/home-icon";
import { Button, Card, Label, Tap } from "@/shared/components/ui";
import { colors as c } from "@/shared/theme";

export function BookingHistoryLoginDialog({
  visible,
  onCancel,
  onConfirm,
}: {
  visible: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onCancel}
    >
      <View
        style={{
          flex: 1,
          backgroundColor: "#102A4380",
          justifyContent: "center",
          alignItems: "center",
          padding: 24,
        }}
      >
        <Card style={{ width: "100%", maxWidth: 360, padding: 24, gap: 16 }}>
          <View accessibilityViewIsModal style={{ gap: 16 }}>
            <View
              style={{
                width: 52,
                height: 52,
                borderRadius: 26,
                backgroundColor: c.tint,
                alignItems: "center",
                justifyContent: "center",
                alignSelf: "center",
              }}
            >
              <HomeIcon name="clock" color={c.primary} size={26} />
            </View>
            <Label
              accessibilityRole="header"
              size={20}
              weight="bold"
              style={{ textAlign: "center" }}
            >
              Đăng nhập để xem lịch sử
            </Label>
            <Label size={14} muted style={{ textAlign: "center" }}>
              Bạn cần đăng nhập để xem các đơn đặt vé và quản lý hành trình của
              mình.
            </Label>
            <Button title="Đăng nhập" onPress={onConfirm} />
            <Tap
              label="Để sau"
              onPress={onCancel}
              style={{ alignItems: "center" }}
            >
              <Label weight="semibold" muted>
                Để sau
              </Label>
            </Tap>
          </View>
        </Card>
      </View>
    </Modal>
  );
}
