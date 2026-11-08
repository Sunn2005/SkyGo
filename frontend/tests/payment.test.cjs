const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const ts = require("typescript");
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "skygo-payment-"));
for (const [source, target] of [
  ["shared/api/client", "client"],
  ["modules/payment/services/payment-service", "payment-service"],
  ["modules/payment/services/payment-state", "payment-state"],
  ["modules/payment/services/known-bookings", "known-bookings"],
  ["modules/payment/services/qr-code", "qr-code"],
]) {
  const sourceText = fs
    .readFileSync(path.join(__dirname, "../src", source + ".ts"), "utf8")
    .replace(
      'import { Platform } from "react-native";',
      'const Platform = { OS: "ios" };',
    )
    .replace('"@/shared/api/client"', '"./client"')
    .replace(
      '"qrcode-generator"',
      JSON.stringify(require.resolve("qrcode-generator")),
    );
  fs.writeFileSync(
    path.join(dir, target + ".js"),
    ts.transpileModule(sourceText, {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
        esModuleInterop: true,
      },
    }).outputText,
  );
}
const originalFetch = global.fetch;
after(() => {
  global.fetch = originalFetch;
  fs.rmSync(dir, { recursive: true, force: true });
});
const { paymentApi } = require(path.join(dir, "payment-service.js"));
const {
  isBookingPaid,
  holdExpired,
  canPay,
  linkMatchesBooking,
  serverNow,
  paymentMessage,
  needsReconciliation,
} = require(path.join(dir, "payment-state.js"));
const base = {
  bookingId: "real-booking",
  bookingStatus: "PENDING_PAYMENT",
  serverTime: "2026-11-07T05:00:00Z",
  currency: "VND",
  payment: { paymentId: "pay-1", status: "PENDING" },
  price: { totalAmount: 3564000 },
  seatHold: {
    seatHoldId: "hold-1",
    status: "ACTIVE",
    expiresAt: "2026-11-07T05:15:00Z",
    expired: false,
  },
};
const link = {
  bookingId: "real-booking",
  seatHoldId: "hold-1",
  expiresAt: base.seatHold.expiresAt,
  paymentLink: {
    paymentId: "pay-1",
    status: "PENDING",
    amount: 3564000,
    currency: "VND",
  },
};
const copy = () => structuredClone(base);

test("payment endpoints use authenticated BE only; POST sends no amount or webhook", async () => {
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return new Response(
      JSON.stringify({ data: { bookingId: "real-booking" } }),
    );
  };
  const abort = new AbortController();
  await paymentApi.detail("token", "booking/encoded", abort.signal);
  await paymentApi.link("token", "real-booking");
  await paymentApi.open("token", "real-booking");
  assert.ok(calls[0].url.endsWith("/api/v1/bookings/booking%2Fencoded"));
  assert.ok(calls[1].url.endsWith("/payment-link"));
  assert.ok(calls[2].url.endsWith("/open-for-payment"));
  assert.equal(calls[0].method, "GET");
  for (const call of calls) {
    assert.equal(call.headers.Authorization, "Bearer token");
    assert.equal(call.body, undefined);
    assert.ok(!call.url.includes("webhook"));
  }
  assert.equal(calls[1].method, "POST");
  assert.equal(calls[2].method, "POST");
});
test("only PAID plus SUCCEEDED permits success; redirects and a single successful field do not", () => {
  assert.equal(isBookingPaid(base), false);
  assert.equal(isBookingPaid({ ...base, bookingStatus: "PAID" }), false);
  assert.equal(
    isBookingPaid({ ...base, payment: { status: "SUCCEEDED" } }),
    false,
  );
  assert.equal(
    isBookingPaid({
      ...base,
      bookingStatus: "PAID",
      payment: { status: "SUCCEEDED" },
    }),
    true,
  );
});
test("server clock controls expiry even when phone clock is wrong; explicit expired flag wins", () => {
  const receivedAt = 100000;
  assert.equal(
    serverNow(base, receivedAt, receivedAt + 1000),
    Date.parse(base.serverTime) + 1000,
  );
  assert.equal(canPay(base, receivedAt, receivedAt + 899000), true);
  assert.equal(canPay(base, receivedAt, receivedAt + 900000), false);
  assert.equal(
    holdExpired(
      { ...base, seatHold: { ...base.seatHold, expired: true } },
      receivedAt,
      receivedAt,
    ),
    true,
  );
  assert.equal(
    canPay({ ...base, seatHold: null }, receivedAt, receivedAt),
    false,
  );
});
test("reconciliation, consumed holds and closed bookings never display a payable QR", () => {
  for (const change of [
    { payment: { status: "RECONCILIATION_REQUIRED" } },
    { bookingStatus: "RECONCILIATION_REQUIRED" },
    { seatHold: { ...base.seatHold, status: "CONSUMED" } },
    { bookingStatus: "EXPIRED" },
    { bookingStatus: "PAID", payment: { status: "SUCCEEDED" } },
  ])
    assert.equal(canPay({ ...base, ...change }, 0, 0), false);
  assert.equal(
    needsReconciliation({
      ...base,
      payment: { status: "RECONCILIATION_REQUIRED" },
    }),
    true,
  );
});
test("QR must match current booking, hold, payment, amount, currency and link status", () => {
  assert.equal(linkMatchesBooking(link, base), true);
  for (const value of [
    { ...link, bookingId: "other" },
    { ...link, seatHoldId: "old-hold" },
    ...[
      { paymentId: "other" },
      { amount: 1 },
      { currency: "USD" },
      { status: "EXPIRED" },
    ].map((change) => ({
      ...link,
      paymentLink: { ...link.paymentLink, ...change },
    })),
  ])
    assert.equal(linkMatchesBooking(value, base), false);
});
test("uncertain/configuration responses preserve server errors and show actionable messages", async () => {
  for (const code of [
    "PAYOS_NOT_CONFIGURED",
    "PAYMENT_LINK_UNVERIFIED",
    "PAYMENT_STATUS_UNVERIFIED",
    "PAYMENT_RECONCILIATION_REQUIRED",
  ]) {
    global.fetch = async () =>
      new Response(
        JSON.stringify({ error: { code, message: "backend message" } }),
        { status: 503 },
      );
    await assert.rejects(
      paymentApi.link("token", "real-booking"),
      (error) => error.code === code,
    );
    assert.ok(paymentMessage(code).length > 20);
  }
});
test("known booking IDs survive reload, isolate accounts, contain no passenger/payment data", () => {
  const { knownBookingIds, rememberBookingId } = require(
    path.join(dir, "known-bookings.js"),
  );
  const values = new Map();
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  };
  rememberBookingId("user-a", "a", storage);
  rememberBookingId("user-a", "b", storage);
  rememberBookingId("user-a", "a", storage);
  assert.deepEqual(knownBookingIds("user-a", storage), ["a", "b"]);
  assert.deepEqual(knownBookingIds("user-b", storage), []);
  assert.deepEqual(knownBookingIds("guest", storage), []);
  assert.equal([...values.values()][0], '["a","b"]');
  const broken = {
    getItem: () => {
      throw Error("storage blocked");
    },
    setItem: () => {
      throw Error("storage blocked");
    },
  };
  assert.doesNotThrow(() =>
    rememberBookingId("user-a", "committed-booking", broken),
  );
});
test("PayOS payload renders a real GIF locally with a quiet zone and preserves UTF-8", () => {
  const { paymentQrImage } = require(path.join(dir, "qr-code.js"));
  const payload =
    "00020101021238570010A0000007270127000697042201131234567890123520400005303704540735640005802VN62200816SKYGO PAYMENT TEST6304ABCD";
  const uri = paymentQrImage(payload);
  assert.ok(uri.startsWith("data:image/gif;base64,"));
  const bytes = Buffer.from(uri.split(",")[1], "base64");
  assert.ok(bytes.toString("ascii", 0, 6).startsWith("GIF8"));
  assert.equal(bytes.readUInt16LE(6), bytes.readUInt16LE(8));
  assert.ok(bytes.readUInt16LE(6) > 150);
  const qrFactory = require("qrcode-generator");
  assert.deepEqual(qrFactory.stringToBytes("Thanh toán Đ"), [
    ...Buffer.from("Thanh toán Đ", "utf8"),
  ]);
  assert.throws(() => paymentQrImage(""));
});
