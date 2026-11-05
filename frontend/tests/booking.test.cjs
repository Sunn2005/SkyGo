const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const ts = require("typescript");
const compiled = fs.mkdtempSync(path.join(os.tmpdir(), "skygo-booking-test-"));
for (const file of [
  "booking/services/booking-rules",
  "booking/data/booking-snapshot",
  "booking/data/mock-booking-service",
  "flights/data/flights",
  "booking/services/reservation-service",
]) {
  const target = path.join(compiled, `${file}.js`);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(
    target,
    ts.transpileModule(
      fs.readFileSync(
        path.join(__dirname, "../src/modules", `${file}.ts`),
        "utf8",
      ),
      {
        compilerOptions: {
          module: ts.ModuleKind.CommonJS,
          target: ts.ScriptTarget.ES2022,
        },
      },
    ).outputText,
  );
}
after(() => fs.rmSync(compiled, { recursive: true, force: true }));
const { quote, travellerErrors, bookingErrors, includedBagWeight } = require(
  path.join(compiled, "booking/services/booking-rules.js"),
);
const { createMockBookingService } = require(
  path.join(compiled, "booking/data/mock-booking-service.js"),
);
const { flights } = require(path.join(compiled, "flights/data/flights.js"));
const person = () => ({
  firstName: "An",
  lastName: "Nguyễn",
  birthDate: "12/03/1990",
  gender: "Nam",
  extraBags: {},
  documentType: "cccd",
  documentNumber: "012345678901",
  nationality: "",
  issuingCountry: "",
  documentExpiry: "",
});
const payload = () => ({
  search: {
    type: "one-way",
    from: "SGN",
    to: "HAN",
    date: "2026-11-16",
    secondDate: "2026-11-23",
    count: 1,
    cabin: "Phổ thông",
  },
  selected: [flights[0]],
  travellers: [person()],
  email: "test@example.com",
  phone: "0912345678",
});
const memory = () => {
  const map = new Map();
  return {
    getItem: (k) => map.get(k) ?? null,
    setItem: (k, v) => map.set(k, v),
  };
};

test("round trip: two passengers, one extra outbound bag totals 6,301,000 VND", () => {
  const p = payload();
  p.search.type = "round-trip";
  p.search.count = 2;
  const travellers = [person(), person()];
  travellers[0].extraBags[0] = 1;
  assert.equal(
    quote(p.search, [flights[0], flights[0]], travellers).total,
    6301000,
  );
  assert.equal(
    quote(
      { ...p.search, cabin: "Thương gia" },
      [flights[0], flights[0]],
      travellers,
    ).total,
    16153000,
  );
  assert.equal(includedBagWeight("Thương gia"), 32);
  assert.equal(includedBagWeight("Phổ thông"), 23);
});
test("validates CCCD, passport through return date, age, dates and 1–4 adults", () => {
  const p = person();
  assert.deepEqual(travellerErrors(p, "2026-11-16"), {});
  assert.ok(
    travellerErrors({ ...p, documentNumber: "123" }, "2026-11-16")
      .documentNumber,
  );
  const passport = {
    ...p,
    documentType: "passport",
    documentNumber: "B1234567",
    nationality: "Việt Nam",
    issuingCountry: "Việt Nam",
    documentExpiry: "20/11/2026",
  };
  assert.ok(
    travellerErrors(passport, "2026-11-16", "2026-11-23").documentExpiry,
  );
  assert.deepEqual(
    travellerErrors(
      { ...passport, documentExpiry: "23/11/2026" },
      "2026-11-16",
      "2026-11-23",
    ),
    {},
  );
  assert.ok(
    travellerErrors({ ...p, birthDate: "31/02/2000" }, "2026-11-16").birthDate,
  );
  assert.ok(
    travellerErrors({ ...p, birthDate: "17/11/2014" }, "2026-11-16").birthDate,
  );
  const draft = payload();
  for (const count of [0, 5, 1.5])
    assert.ok(
      bookingErrors(
        { ...draft.search, count },
        draft.selected,
        draft.travellers,
        draft.email,
        draft.phone,
      ).booking,
    );
  assert.ok(
    bookingErrors(
      { ...draft.search, type: "round-trip", secondDate: "2026-11-15" },
      draft.selected,
      draft.travellers,
      draft.email,
      draft.phone,
    ).search,
  );
  assert.ok(
    bookingErrors(
      draft.search,
      draft.selected,
      [{ ...p, extraBags: { 0: 5 } }],
      draft.email,
      draft.phone,
    )["0-extraBags"],
  );
});
test("requires login; rejects invalid passengers and unknown flights; recalculates price", async () => {
  const api = createMockBookingService(memory());
  await assert.rejects(api.create("guest", payload(), "key"), /đăng nhập/);
  const invalid = payload();
  invalid.travellers[0].documentNumber = "";
  await assert.rejects(api.create("u1", invalid, "key"), /chưa hợp lệ/);
  await assert.rejects(
    api.create(
      "u1",
      { ...payload(), selected: [{ ...flights[0], id: "missing" }] },
      "key",
    ),
    /không còn/,
  );
  const booking = await api.create(
    "u1",
    { ...payload(), selected: [{ ...flights[0], price: 1 }] },
    "key",
  );
  assert.equal(booking.receipt.total, 1494000);
  assert.equal(booking.status, "pending_payment");
  assert.deepEqual(booking.payments, []);
});
test("concurrent confirmation is idempotent; conflicting reuse is rejected", async () => {
  const api = createMockBookingService(memory());
  const [a, b] = await Promise.all([
    api.create("u1", payload(), "same"),
    api.create("u1", payload(), "same"),
  ]);
  assert.equal(a.id, b.id);
  assert.equal((await api.list("u1")).length, 1);
  await assert.rejects(
    api.create("u1", { ...payload(), email: "other@example.com" }, "same"),
    /thay đổi/,
  );
});
test("cancel/fail/expire → retry retains booking, reuses pending attempt, paid is final", async () => {
  const api = createMockBookingService(memory());
  let booking = await api.create("u1", payload(), "key");
  for (const outcome of ["cancelled", "failed", "expired", "paid"]) {
    booking = await api.createPayment("u1", booking.id);
    const attempt = booking.payments.at(-1);
    assert.equal(
      (await api.createPayment("u1", booking.id)).payments.at(-1).id,
      attempt.id,
    );
    await api.simulateWebhook("u1", booking.id, attempt.id, outcome);
    booking = await api.get("u1", booking.id);
    assert.equal(booking.payments.at(-1).status, outcome);
    assert.equal(
      booking.status,
      outcome === "paid" ? "confirmed" : "pending_payment",
    );
  }
  assert.equal(booking.payments.length, 4);
  await api.simulateWebhook(
    "u1",
    booking.id,
    booking.payments.at(-1).id,
    "failed",
  );
  assert.equal((await api.createPayment("u1", booking.id)).payments.length, 4);
  assert.equal((await api.get("u1", booking.id)).status, "confirmed");
});
test("persisted pending booking survives adapter restart and isolates accounts", async () => {
  const storage = memory();
  const first = createMockBookingService(storage);
  const booking = await first.create("u1", payload(), "key");
  await first.createPayment("u1", booking.id);
  const restarted = createMockBookingService(storage);
  assert.equal((await restarted.list("u1"))[0].id, booking.id);
  assert.equal((await restarted.list("u1"))[0].payments[0].status, "pending");
  assert.deepEqual(await restarted.list("u2"), []);
  await assert.rejects(restarted.get("u2", booking.id), /Không tìm thấy/);
});
test("15 minute expiry cannot be paid by a stale event; retry starts a new attempt", async () => {
  let now = Date.UTC(2026, 9, 31);
  const api = createMockBookingService(memory(), () => now);
  let b = await api.create("u1", payload(), "key");
  b = await api.createPayment("u1", b.id);
  const old = b.payments[0].id;
  now += 15 * 60000;
  await api.simulateWebhook("u1", b.id, old, "paid");
  b = await api.get("u1", b.id);
  assert.equal(b.status, "pending_payment");
  assert.equal(b.payments[0].status, "expired");
  b = await api.createPayment("u1", b.id);
  assert.notEqual(b.payments.at(-1).id, old);
});
test("storage failure reports an error and allows retry without poisoning the queue", async () => {
  const store = memory();
  let fail = true;
  const api = createMockBookingService({
    ...store,
    setItem: (k, v) => {
      if (fail) throw new Error("disk full");
      store.setItem(k, v);
    },
  });
  await assert.rejects(api.create("u1", payload(), "same"), /disk full/);
  fail = false;
  await api.create("u1", payload(), "same");
  assert.equal((await api.list("u1")).length, 1);
});

test("legacy stored booking is normalized without losing its reference, price, payment or retry key", async () => {
  const storage = memory();
  const api = createMockBookingService(storage);
  let booking = await api.create("u1", payload(), "legacy-key");
  booking = await api.createPayment("u1", booking.id);
  const key = "skygo.mock.bookings.v1";
  const old = JSON.parse(storage.getItem(key));
  Object.assign(old[0].receipt, {
    seats: { "0-0": "12A" },
    card: "Mastercard",
    insurance: false,
    taxes: 0,
    seatTotal: 0,
    protection: 0,
  });
  old[0].receipt.travellers[0].bags = 1;
  old[0].receipt.search.onward = "DAD";
  old[0].fingerprint = "old-model-fingerprint";
  storage.setItem(key, JSON.stringify(old));
  const restarted = createMockBookingService(storage);
  const restored = await restarted.get("u1", booking.id);
  assert.deepEqual(restored.receipt, booking.receipt);
  assert.deepEqual(restored.payments, booking.payments);
  assert.equal(
    (await restarted.create("u1", payload(), "legacy-key")).id,
    booking.id,
  );
  assert.equal((await restarted.list("u1")).length, 1);
  const saved = JSON.parse(storage.getItem(key))[0];
  assert.deepEqual(saved.receipt, booking.receipt);
});

const { createReservationService } = require(
  path.join(compiled, "booking/services/reservation-service.js"),
);
test("holds prevent overselling; swaps preserve expiry and failure keeps old seats", () => {
  let now = 1000;
  const api = createReservationService(() => now, 2);
  const s = { ...payload().search, count: 2 };
  const first = api.reserve("a", s, [flights[0]]);
  assert.throws(() => api.reserve("b", s, [flights[0]]), /Không đủ/);
  const other = api.reserve("b", s, [flights[1]]);
  assert.throws(() => api.reserve("a", s, [flights[1]], first), /Không đủ/);
  assert.throws(() => api.reserve("c", s, [flights[0]]), /Không đủ/);
  now += 60000;
  const swap = api.reserve("a", s, [flights[2]], first);
  assert.equal(swap.expiresAt, first.expiresAt);
  api.release(other.id);
  now = first.expiresAt;
  assert.throws(() => api.reserve("a", s, [flights[0]], swap), /hết/);
  assert.ok(api.reserve("b", s, [flights[0]]));
});
test("QR shares the original reservation expiry and cannot renew an expired hold", async () => {
  let now = 1000;
  const api = createMockBookingService(memory(), () => now);
  const expiresAt = new Date(now + 15 * 60000).toISOString();
  let b = await api.create("u1", { ...payload(), expiresAt }, "hold");
  now += 10 * 60000;
  b = await api.createPayment("u1", b.id);
  assert.equal(b.payments[0].expiresAt, expiresAt);
  now += 5 * 60000;
  await api.simulateWebhook("u1", b.id, b.payments[0].id, "paid");
  assert.equal((await api.get("u1", b.id)).status, "pending_payment");
  await assert.rejects(api.createPayment("u1", b.id), /hết hạn/);
});

test("family fares and seat inventory exclude lap infants, validate groups and companions", () => {
  const draft = payload();
  const s = { ...draft.search, children: 1, infants: 1 };
  const adult = { ...person(), kind: "adult" };
  const child = { ...person(), kind: "child", birthDate: "12/03/2018" };
  const infant = {
    ...person(),
    kind: "infant",
    birthDate: "12/03/2025",
    companionIndex: 0,
  };
  assert.equal(quote(s, [flights[0]], [adult, child, infant]).fare, 2988000);
  assert.deepEqual(
    bookingErrors(
      s,
      [flights[0]],
      [adult, child, infant],
      draft.email,
      draft.phone,
    ),
    {},
  );
  assert.ok(
    bookingErrors(
      s,
      [flights[0]],
      [adult, child, { ...infant, companionIndex: 1 }],
      draft.email,
      draft.phone,
    )["2-companionIndex"],
  );
  assert.ok(
    travellerErrors({ ...child, birthDate: "16/11/2014" }, s.date).birthDate,
  );
  assert.ok(
    travellerErrors({ ...infant, birthDate: "16/11/2024" }, s.date).birthDate,
  );
  const api = createReservationService(() => 1000, 2);
  api.reserve("a", s, [flights[0]]);
  assert.throws(() => api.reserve("b", draft.search, [flights[0]]), /Không đủ/);
  const mix = [
    { ...flights[0], cabin: "Phổ thông" },
    { ...flights[0], cabin: "Thương gia" },
  ];
  assert.equal(
    quote({ ...s, type: "round-trip" }, mix, [adult, child, infant]).fare,
    10902000,
  );
});
test("held bookings keep idempotency and original expiry after adapter reload", async () => {
  const storage = memory();
  const input = { ...payload(), expiresAt: new Date(1000000).toISOString() };
  const first = createMockBookingService(storage, () => 1000);
  const original = await first.create("u1", input, "same-hold");
  const restarted = createMockBookingService(storage, () => 2000);
  assert.equal(
    (await restarted.create("u1", input, "same-hold")).id,
    original.id,
  );
});

test("one reservation cannot create two bookings with different retry keys", async () => {
  const api = createMockBookingService(memory(), () => 1000);
  const input = {
    ...payload(),
    reservationId: "hold-one",
    expiresAt: new Date(1000000).toISOString(),
  };
  const a = await api.create("u1", input, "key-a");
  const b = await api.create("u1", input, "key-b");
  assert.equal(a.id, b.id);
  assert.equal((await api.list("u1")).length, 1);
  await assert.rejects(
    api.create("u1", { ...input, email: "changed@example.com" }, "key-c"),
    /thay đổi/,
  );
});
