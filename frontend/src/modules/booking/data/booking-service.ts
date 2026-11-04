import "expo-sqlite/localStorage/install";
import { createMockBookingService } from "./mock-booking-service";

// Replace this adapter with HTTP calls once the backend contract is available.
export const bookingService = createMockBookingService({
  getItem: (key) => localStorage.getItem(key),
  setItem: (key, value) => localStorage.setItem(key, value),
});
