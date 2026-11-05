import { Modal, View } from "react-native";
import { usePathname, router } from "expo-router";
import { Button, Card, Label } from "@/shared/components/ui";
import { useBooking } from "../context/booking-context";
export function ReservationNotice() {
  const b = useBooking();
  const path = usePathname();
  const expired = b.remaining === 0 && b.currentBooking?.status !== "confirmed";
  const warning =
    b.remaining !== null &&
    b.remaining > 0 &&
    b.remaining <= 300 &&
    !b.warningDismissed;
  const visible = Boolean(
    b.reservation &&
    (expired || warning) &&
    !["/login", "/register"].includes(path),
  );
  if (!visible) return null;
  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={() => {
        if (!expired) b.setWarningDismissed(true);
      }}
    >
      <View
        style={{
          flex: 1,
          backgroundColor: "#102A4380",
          justifyContent: "center",
          padding: 24,
        }}
      >
        <Card>
          <Label size={22} weight="bold">
            {expired ? "Thời gian giữ chỗ đã hết" : "Sắp hết thời gian giữ chỗ"}
          </Label>
          <Label>
            {expired
              ? "Vui lòng tìm và chọn lại chuyến bay. Thông tin hành khách và liên hệ đã nhập được giữ lại trong phiên này."
              : `Bạn còn ${Math.ceil((b.remaining ?? 0) / 60)} phút để hoàn tất đặt vé và thanh toán.`}
          </Label>
          <Button
            title={expired ? "Tìm lại chuyến bay" : "Tiếp tục đặt vé"}
            onPress={() => {
              if (expired) {
                b.startSearch();
                router.replace("/search");
              } else b.setWarningDismissed(true);
            }}
          />
        </Card>
      </View>
    </Modal>
  );
}
