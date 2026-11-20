import { Modal, View } from "react-native";
import { HomeIcon } from "@/modules/flights/components/home-icon";
import { Button, Card, Label, Tap } from "@/shared/components/ui";
import { colors as c } from "@/shared/theme";

export function PaymentExitDialog({
  visible,
  remaining,
  busy,
  onStay,
  onLeave,
}: {
  visible: boolean;
  remaining: number;
  busy: boolean;
  onStay: () => void;
  onLeave: () => void;
}) {
  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onStay}
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
        <Card style={{ width: "100%", maxWidth: 360, padding: 24 }}>
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
              size={20}
              weight="bold"
              accessibilityRole="header"
              style={{ textAlign: "center" }}
            >
              Quay lại?
            </Label>
            <Label size={14} muted style={{ textAlign: "center" }}>
              {remaining > 0
                ? "Chúng tôi vẫn đang giữ chỗ cho bạn trên chuyến bay này. Bạn có chắc chắn muốn quay lại không?"
                : "Bạn có chắc chắn muốn rời màn hình thanh toán? Bạn có thể kiểm tra đơn trong mục Lịch sử."}
            </Label>
            {remaining > 0 && (
              <>
                <Label
                  weight="semibold"
                  color={c.primary}
                  style={{ textAlign: "center" }}
                >
                  Giữ chỗ còn {Math.floor(remaining / 60)}:
                  {String(remaining % 60).padStart(2, "0")}
                </Label>
                <Label size={12} muted style={{ textAlign: "center" }}>
                  Đơn và mã QR vẫn có hiệu lực theo hạn giữ chỗ hiện tại. Bạn có
                  thể tiếp tục thanh toán trong mục Lịch sử.
                </Label>
              </>
            )}
            <Button title="Tiếp tục thanh toán" onPress={onStay} />
            <Tap
              label="Quay lại"
              disabled={busy}
              onPress={onLeave}
              style={{ alignItems: "center" }}
            >
              <Label weight="semibold" muted>
                {busy ? "Đang kiểm tra thanh toán…" : "Quay lại"}
              </Label>
            </Tap>
          </View>
        </Card>
      </View>
    </Modal>
  );
}
