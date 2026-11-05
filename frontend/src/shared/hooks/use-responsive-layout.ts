import { Platform, useWindowDimensions } from "react-native";

// Match the app container, not physical display pixels. Keep system font scaling.
export function useResponsiveLayout() {
  const { width, fontScale } = useWindowDimensions();
  const contentWidth = Math.min(width, Platform.OS === "web" ? 402 : 600);
  return {
    compact: contentWidth < 360 || fontScale > 1.2,
    stackFields: contentWidth < 375 || fontScale > 1.2,
    stackDates: contentWidth < 430 || fontScale > 1.15,
    horizontalPadding: contentWidth < 360 ? 16 : 20,
  };
}
