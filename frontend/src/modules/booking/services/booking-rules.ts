import type { Search, Flight } from "../../flights/types";
import type { Traveller, BookingPrice } from "../types";

export const isDomestic = (search: Search) =>
  [search.from, search.to].every((code) =>
    ["SGN", "HAN", "DAD", "PQC", "CXR"].includes(code),
  );
export const EXTRA_BAG_PRICE = 325000;
export const includedBagWeight = (cabin: Search["cabin"]) =>
  cabin === "Thương gia" ? 32 : 23;
export const flightPrice = (flight: Flight, cabin: Search["cabin"]) =>
  cabin === "Thương gia" ? (flight.businessPrice ?? 3957000) : flight.price;
export function quote(
  search: Search,
  selected: Flight[],
  travellers: Traveller[],
): BookingPrice {
  const fare = selected.reduce((sum, f) => {
    const price = flightPrice(f, f.cabin ?? search.cabin);
    return (
      sum +
      price * search.count +
      Math.round(price * 0.9 * (search.children ?? 0)) +
      Math.round(price * 0.1 * (search.infants ?? 0))
    );
  }, 0);
  const baggage = travellers.reduce(
    (sum, p) =>
      sum +
      (p.kind === "infant"
        ? 0
        : selected.reduce(
            (n, _, leg) => n + (p.extraBags[leg] ?? 0) * EXTRA_BAG_PRICE,
            0,
          )),
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
  const date = new Date(departure + "T12:00:00");
  const twelve = new Date(date);
  twelve.setFullYear(twelve.getFullYear() - 12);
  const two = new Date(date);
  two.setFullYear(two.getFullYear() - 2);
  const birthday = birth ? new Date(birth + "T12:00:00") : null;
  const kind = p.kind ?? "adult";
  const validAge =
    birthday &&
    birthday <= date &&
    (kind === "adult"
      ? birthday <= twelve
      : kind === "child"
        ? birthday > twelve && birthday <= two
        : birthday > two);
  if (!birth || birth < "1900-01-01" || !validAge)
    errors.birthDate = `Ngày sinh không đúng nhóm ${kind === "adult" ? "từ 12 tuổi" : kind === "child" ? "2 đến dưới 12 tuổi" : "dưới 2 tuổi"} tại ngày khởi hành.`;
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
    !Number.isInteger(search.children ?? 0) ||
    (search.children ?? 0) < 0 ||
    !Number.isInteger(search.infants ?? 0) ||
    (search.infants ?? 0) < 0 ||
    search.count + (search.children ?? 0) > 4 ||
    (search.infants ?? 0) > search.count ||
    travellers.length !==
      search.count + (search.children ?? 0) + (search.infants ?? 0)
  )
    errors.booking =
      "Cần ít nhất 1 người lớn, tối đa 4 khách có ghế riêng và mỗi người lớn đi cùng tối đa 1 em bé.";
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
  if (
    selected.some(
      (f) => f.cabin && !["Phổ thông", "Thương gia"].includes(f.cabin),
    )
  )
    errors.cabin = "Hạng vé từng chặng chưa hợp lệ.";
  const legCount = search.type === "round-trip" ? 2 : 1;
  if (selected.length !== legCount)
    errors.flight = "Vui lòng chọn đủ chuyến bay.";
  const actual = { adult: 0, child: 0, infant: 0 };
  const companions = new Set<number>();
  travellers.forEach((p, i) => {
    const kind = p.kind ?? "adult";
    if (!(kind in actual)) {
      errors[`${i}-kind`] = "Nhóm hành khách không hợp lệ.";
      return;
    }
    actual[kind]++;
    if (kind === "infant") {
      const companion = p.companionIndex;
      if (
        companion === undefined ||
        companions.has(companion) ||
        !travellers[companion] ||
        (travellers[companion].kind ?? "adult") !== "adult"
      )
        errors[`${i}-companionIndex`] =
          "Chọn một người lớn đi kèm riêng cho em bé.";
      else companions.add(companion);
      if (Object.values(p.extraBags).some((n) => n !== 0))
        errors[`${i}-extraBags`] =
          "Em bé không mua hành lý riêng; mua trên người lớn đi kèm.";
    }
    if (!isDomestic(search) && p.documentType !== "passport")
      errors[`${i}-documentType`] = "Chuyến quốc tế bắt buộc hộ chiếu.";
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
          !Number.isInteger(n) ||
          n < 0 ||
          n > 4,
      )
    )
      errors[`${i}-extraBags`] =
        "Mỗi người được mua thêm tối đa 4 kiện mỗi chặng.";
  });
  if (
    actual.adult !== search.count ||
    actual.child !== (search.children ?? 0) ||
    actual.infant !== (search.infants ?? 0)
  )
    errors.booking = "Nhóm hành khách chưa khớp tìm kiếm.";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()))
    errors.email = "Vui lòng nhập email hợp lệ.";
  if (
    !/^\+?[\d\s()-]{8,20}$/.test(phone.trim()) ||
    phone.replace(/\D/g, "").length < 8
  )
    errors.phone = "Vui lòng nhập số điện thoại hợp lệ.";
  return errors;
}

export const passengerSummary = (s: Search) =>
  [
    `${s.count} người lớn`,
    s.children ? `${s.children} trẻ em` : "",
    s.infants ? `${s.infants} em bé` : "",
  ]
    .filter(Boolean)
    .join(" · ");
