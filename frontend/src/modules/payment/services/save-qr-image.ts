import { Platform } from "react-native";
import {
  ImageManipulator,
  SaveFormat,
  type ImageRef,
} from "expo-image-manipulator";

export async function saveQrImage(uri: string, name: string) {
  const context = ImageManipulator.manipulate(uri);
  let image: ImageRef | undefined;
  try {
    image = await context.renderAsync();
    // Encode the image as PNG; renaming the QR's GIF/SVG URI is not a conversion.
    const png = await image.saveAsync({
      format: SaveFormat.PNG,
      base64: Platform.OS === "web",
    });
    if (Platform.OS === "web") {
      if (!png.base64)
        throw new Error("Chưa thể tạo ảnh PNG. Vui lòng thử lại.");
      const anchor = document.createElement("a");
      anchor.href = `data:image/png;base64,${png.base64}`;
      anchor.download = `${name.replace(/[^a-zA-Z0-9_-]/g, "-")}.png`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(png.uri);
      return "Đã tải ảnh QR PNG.";
    }
    if (Platform.OS === "ios") {
      // This entry point supports add-only access without reading the library.
      const media = await import("expo-media-library/legacy");
      await media.saveToLibraryAsync(png.uri);
    } else {
      const media = await import("expo-media-library");
      if (Number(Platform.Version) < 29) {
        const permission = await media.requestPermissionsAsync(true, []);
        if (!permission.granted)
          throw new Error(
            "Hãy cho phép SkyGo lưu ảnh trong Cài đặt rồi thử lại.",
          );
      }
      // Android 10+ can add its own images without reading the user's library.
      await media.Asset.create(png.uri);
    }
    return "Đã lưu ảnh QR PNG vào thư viện ảnh.";
  } finally {
    image?.release();
    context.release();
  }
}
