import { Modal, View } from "react-native";
import { Button, Card, Label, Tap } from "@/shared/components/ui";
import { HomeIcon } from "./home-icon";
import { colors as c } from "@/shared/theme";

export function LoginRequiredDialog({
  visible,
  onLogin,
  onDismiss,
}: {
  visible: boolean;
  onLogin: () => void;
  onDismiss: () => void;
}) {
  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onDismiss}
    >
      <View
        style={{
          flex: 1,
          justifyContent: "center",
          alignItems: "center",
          padding: 24,
          backgroundColor: "#102A4380",
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
              <HomeIcon name="user" color={c.primary} size={26} />
            </View>
            <Label
              size={20}
              weight="bold"
              accessibilityRole="header"
              style={{ textAlign: "center" }}
            >
              Đăng nhập để tiếp tục đặt vé
            </Label>
            <Label size={14} muted style={{ textAlign: "center" }}>
              Bạn cần đăng nhập để giữ chỗ và nhập thông tin hành khách. Sau khi
              đăng nhập, SkyGo sẽ tiếp tục với chuyến bay bạn vừa chọn.
            </Label>
            <Button title="Đăng nhập" onPress={onLogin} />
            <Tap
              label="Để sau, tiếp tục xem chuyến bay"
              onPress={onDismiss}
              style={{ alignItems: "center" }}
            >
              <Label muted weight="semibold">
                Để sau
              </Label>
            </Tap>
          </View>
        </Card>
      </View>
    </Modal>
  );
}
