import createQrCode from "qrcode-generator";
// Preserve the exact UTF-8 payload, including a bank description with accents.
Object.assign(createQrCode, {
  stringToBytes: (value: string) =>
    Array.from(
      encodeURIComponent(value).replace(/%([0-9A-F]{2})/g, (_, hex: string) =>
        String.fromCharCode(parseInt(hex, 16)),
      ),
      (char) => char.charCodeAt(0),
    ),
});
export function paymentQrImage(payload: string) {
  if (!payload) throw new Error("Máy chủ chưa trả dữ liệu QR.");
  const qr = createQrCode(0, "M");
  qr.addData(payload, "Byte");
  qr.make();
  return qr.createDataURL(6, 24);
}
