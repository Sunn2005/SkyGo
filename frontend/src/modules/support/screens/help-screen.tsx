import { useState } from "react";
import { Card, Label, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";

const faqs = [
  [
    "Đây có phải đặt vé thật không?",
    "SkyGo hiện là bản trải nghiệm với dữ liệu mẫu. Thanh toán không trừ tiền, không tạo vé của hãng và không gửi email.",
  ],
  [
    "Có thể thử toàn bộ hành trình không?",
    "Có. Tìm chuyến một chiều hoặc khứ hồi, đăng nhập trước khi đặt vé, nhập thông tin và giấy tờ giả, chọn hành lý, xem lại rồi thử thanh toán PayOS mô phỏng.",
  ],
  [
    "Giá vé và tiền tệ được tính thế nào?",
    "Giá bằng VND, đã gồm thuế phí. Phổ thông gồm 1 kiện 23 kg; Thương gia gồm 1 kiện tối đa 32 kg. Mua thêm tối đa 1 kiện 23 kg giá 324.000đ/người/chặng.",
  ],
  [
    "Vé khứ hồi hoạt động ra sao?",
    "Chọn chuyến đi rồi chuyến về, dùng chung danh sách 1–4 hành khách. Hành lý mua thêm chọn riêng cho từng người/từng chặng. Thanh toán chung một booking.",
  ],
  [
    "Làm sao thử thanh toán thất bại?",
    "Trên màn PayOS mô phỏng, chọn thành công, thất bại, hủy hoặc hết hạn. Thử thanh toán lại vẫn dùng cùng mã booking.",
  ],
  [
    "Dữ liệu của tôi được lưu ở đâu?",
    "Booking đã tạo lưu trên thiết bị theo tài khoản. Tải lại app cần đăng nhập lại rồi mở Tài khoản → Đặt chỗ của tôi. Tài khoản mới và bản nháp vẫn chỉ lưu trong phiên; dùng thông tin giả khi trải nghiệm.",
  ],
];
export default function HelpScreen() {
  const [open, setOpen] = useState<number | null>(0);
  return (
    <Screen title="Trợ giúp">
      <Label size={24} weight="bold">
        Đồng hành cùng bạn
      </Label>
      <Label muted>Câu hỏi thường gặp về bản trải nghiệm SkyGo.</Label>
      {faqs.map(([question, answer], i) => (
        <Card key={question}>
          <Tap label={question} onPress={() => setOpen(open === i ? null : i)}>
            <Label weight="semibold" color={open === i ? c.primary : c.ink}>
              {question}
            </Label>
          </Tap>
          {open === i && (
            <Label size={14} muted>
              {answer}
            </Label>
          )}
        </Card>
      ))}
    </Screen>
  );
}
