import type { Search, Flight } from "../../flights/types";
import type { Traveller, BookingPrice } from "../types";

export const EXTRA_BAG_PRICE = 324000;
export const includedBagWeight = (cabin: Search["cabin"]) =>
  cabin === "Thương gia" ? 32 : 23;
export const flightPrice = (flight: Flight, cabin: Search["cabin"]) =>
  cabin === "Thương gia" ? (flight.businessPrice ?? 3957000) : flight.price;
export function quote(
  search: Search,
  selected: Flight[],
  travellers: Traveller[],
): BookingPrice {
  const fare =
    selected.reduce((sum, f) => sum + flightPrice(f, search.cabin), 0) *
    search.count;
  const baggage = travellers.reduce(
    (sum, p) =>
      sum +
      selected.reduce(
        (n, _, leg) => n + (p.extraBags[leg] ?? 0) * EXTRA_BAG_PRICE,
        0,
      ),
    0,
  );
  return {
    fare,
    baggage,
    total: fare + baggage,
  };
}
function dateISO(value: string) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) return null;
  const iso = `${match[3]}-${match[2]}-${match[1]}`;
  const date = new Date(iso + "T12:00:00");
  return Number.isFinite(date.getTime()) &&
    date.getFullYear() === +match[3] &&
    date.getMonth() + 1 === +match[2] &&
    date.getDate() === +match[1]
    ? iso
    : null;
}
export function travellerErrors(
  p: Traveller,
  departure: string,
  lastDate = departure,
) {
  const errors: Record<string, string> = {};
  if (!p.firstName.trim()) errors.firstName = "Vui lòng nhập tên.";
  if (!p.lastName.trim()) errors.lastName = "Vui lòng nhập họ.";
  const birth = dateISO(p.birthDate);
  const cutoff = new Date(departure + "T12:00:00");
  cutoff.setFullYear(cutoff.getFullYear() - 12);
  if (!birth || birth < "1900-01-01" || new Date(birth + "T12:00:00") > cutoff)
    errors.birthDate = "Ngày hợp lệ, từ 12 tuổi (NN/TT/NNNN).";
  if (!["Nam", "Nữ", "Khác"].includes(p.gender))
    errors.gender = "Chọn giới tính.";
  if (p.documentType === "cccd") {
    if (!/^\d{12}$/.test(p.documentNumber.trim()))
      errors.documentNumber = "CCCD cần đúng 12 chữ số.";
  } else if (p.documentType === "passport") {
    if (!/^[A-Z0-9]{6,12}$/i.test(p.documentNumber.trim()))
      errors.documentNumber = "Hộ chiếu cần 6–12 chữ hoặc số (quy tắc mẫu).";
    if (!p.nationality.trim()) errors.nationality = "Nhập quốc tịch.";
    if (!p.issuingCountry.trim()) errors.issuingCountry = "Nhập quốc gia cấp.";
    const expiry = dateISO(p.documentExpiry);
    if (!expiry || expiry < lastDate)
      errors.documentExpiry = "Giấy tờ phải còn hạn đến hết hành trình.";
  } else errors.documentType = "Chọn CCCD hoặc hộ chiếu.";
  return errors;
}
export function bookingErrors(
  search: Search,
  selected: Flight[],
  travellers: Traveller[],
  email: string,
  phone: string,
) {
  const errors: Record<string, string> = {};
  if (
    !["one-way", "round-trip"].includes(search.type) ||
    !Number.isInteger(search.count) ||
    search.count < 1 ||
    search.count > 4 ||
    travellers.length !== search.count
  )
    errors.booking = "Chỉ hỗ trợ một chiều/khứ hồi, 1–4 người lớn.";
  const validISO = (iso: string) =>
    /^\d{4}-\d{2}-\d{2}$/.test(iso) &&
    dateISO(`${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`) ===
      iso;
  if (
    !validISO(search.date) ||
    (search.type === "round-trip" &&
      (!validISO(search.secondDate) || search.secondDate < search.date)) ||
    search.from === search.to
  )
    errors.search = "Ngày bay hoặc hành trình chưa hợp lệ.";
  if (!["Phổ thông", "Thương gia"].includes(search.cabin))
    errors.cabin = "Chọn hạng vé hợp lệ.";
  const legCount = search.type === "round-trip" ? 2 : 1;
  if (selected.length !== legCount)
    errors.flight = "Vui lòng chọn đủ chuyến bay.";
  travellers.forEach((p, i) => {
    Object.entries(
      travellerErrors(
        p,
        search.date,
        search.type === "round-trip" ? search.secondDate : search.date,
      ),
    ).forEach(([key, message]) => {
      errors[`${i}-${key}`] = message;
    });
    if (
      Object.entries(p.extraBags).some(
        ([leg, n]) =>
          !Number.isInteger(+leg) ||
          +leg < 0 ||
          +leg >= legCount ||
          ![0, 1].includes(n),
      )
    )
      errors[`${i}-extraBags`] =
        "Mỗi người được mua thêm tối đa 1 kiện mỗi chặng.";
  });
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()))
    errors.email = "Vui lòng nhập email hợp lệ.";
  if (
    !/^\+?[\d\s()-]{8,20}$/.test(phone.trim()) ||
    phone.replace(/\D/g, "").length < 8
  )
    errors.phone = "Vui lòng nhập số điện thoại hợp lệ.";
  return errors;
}
