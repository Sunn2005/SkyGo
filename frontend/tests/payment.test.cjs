const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const ts = require("typescript");
global.__qrSavePlatform = { OS: "web", Version: 35 };
global.__qrSaveMock = {};
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "skygo-payment-"));
for (const [source, target] of [
  ["shared/api/client", "client"],
  ["shared/api/offline-cache", "offline-cache"],
  ["modules/payment/services/payment-service", "payment-service"],
  ["modules/flights/services/home-service", "home-service"],
  ["modules/tickets/services/booking-history", "booking-history"],
  ["modules/tickets/services/booking-presentation", "booking-presentation"],
  ["modules/payment/services/payment-state", "payment-state"],
  ["modules/payment/services/known-bookings", "known-bookings"],
  ["modules/payment/services/qr-code", "qr-code"],
  ["modules/payment/services/save-qr-image", "save-qr-image"],
]) {
  const sourceText = fs
    .readFileSync(path.join(__dirname, "../src", source + ".ts"), "utf8")
    .replace(
      'import { Platform } from "react-native";',
      "const Platform = global.__qrSavePlatform;",
    )
    .replace('import "expo-sqlite/localStorage/install";', "")
    .replace('"@/shared/api/client"', '"./client"')
    .replaceAll('"@/shared/api/offline-cache"', '"./offline-cache"')
    .replaceAll('"@/modules/flights/services/home-service"', '"./home-service"')
    .replaceAll(
      '"@/modules/payment/services/payment-service"',
      '"./payment-service"',
    )
    .replaceAll(
      '"@/modules/payment/services/payment-state"',
      '"./payment-state"',
    )
    .replaceAll(
      '"@/modules/payment/services/known-bookings"',
      '"./known-bookings"',
    )
    .replace('"expo-image-manipulator"', '"./image-manipulator"')
    .replaceAll('"expo-media-library/legacy"', '"./media-library"')
    .replaceAll('"expo-media-library"', '"./media-library"')
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
fs.writeFileSync(
  path.join(dir, "image-manipulator.js"),
  `
exports.SaveFormat = { PNG: "png" };
exports.ImageManipulator = { manipulate: (...args) => global.__qrSaveMock.manipulate(...args) };
`,
);
fs.writeFileSync(
  path.join(dir, "media-library.js"),
  `
exports.saveToLibraryAsync = (...args) => global.__qrSaveMock.save(...args);
exports.requestPermissionsAsync = (...args) => global.__qrSaveMock.permission(...args);
exports.Asset = {create: (...args) => global.__qrSaveMock.save(...args)};
`,
);
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
  canResume,
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

test("released holds require rehold; resume respects BE flag, reconciliation and exact cutoff", () => {
  const saved = {
    ...base,
    bookingStatus: "AWAITING_REHOLD",
    requiresRehold: true,
    canResumePayment: true,
    bookingClosesAt: "2026-11-07T06:00:00Z",
    seatHold: { ...base.seatHold, status: "RELEASED" },
  };
  assert.equal(holdExpired(saved, 0, 0), true);
  assert.equal(canPay(saved, 0, 0), false);
  assert.equal(canResume(saved, 0, 0), true);
  assert.equal(canResume(saved, 0, 3600000), false);
  for (const change of [
    { canResumePayment: false },
    { bookingStatus: "CANCELLED" },
    { payment: { status: "RECONCILIATION_REQUIRED" } },
  ])
    assert.equal(canResume({ ...saved, ...change }, 0, 0), false);
  assert.equal(
    canPay({ ...base, bookingClosesAt: base.serverTime }, 0, 0),
    false,
  );
});

test("resume sends confirmation ID/version only after explicit confirmation", async () => {
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return new Response(JSON.stringify({ data: {} }));
  };
  await paymentApi.resume("token", "saved/order");
  await paymentApi.resume("token", "saved/order", {
    expectedSeatHoldId: "browse-hold",
    expectedVersion: 7,
  });
  assert.ok(calls[0].url.endsWith("/saved%2Forder/resume-payment"));
  assert.equal(calls[0].body, undefined);
  assert.deepEqual(JSON.parse(calls[1].body), {
    expectedSeatHoldId: "browse-hold",
    expectedVersion: 7,
  });
  assert.equal(calls[1].headers.Authorization, "Bearer token");
});

test("save QR encodes PNG, downloads a .png on web and saves converted URI to phone photos", async () => {
  const { saveQrImage } = require(path.join(dir, "save-qr-image.js"));
  const previousDocument = global.document;
  const originalRevoke = URL.revokeObjectURL;
  try {
    for (const [os, version] of [
      ["web", 35],
      ["ios", 18],
      ["android", 35],
      ["android", 28],
    ]) {
      Object.assign(global.__qrSavePlatform, { OS: os, Version: version });
      const calls = [];
      global.__qrSaveMock = {
        manipulate: (uri) => {
          assert.equal(uri, "data:image/gif;base64,SOURCE");
          return {
            release: () => calls.push("release-context"),
            renderAsync: async () => ({
              release: () => calls.push("release-image"),
              saveAsync: async (options) => {
                assert.equal(options.format, "png");
                assert.equal(options.base64, os === "web");
                return {
                  uri:
                    os === "web" ? "blob:png-result" : "file:///cache/qr.png",
                  base64: "PNG_BYTES",
                };
              },
            }),
          };
        },
        save: async (uri) => {
          assert.equal(uri, "file:///cache/qr.png");
          calls.push("saved");
        },
        permission: async (writeOnly, granular) => {
          assert.equal(writeOnly, true);
          assert.deepEqual(granular, []);
          calls.push("permission");
          return { granted: true };
        },
      };
      const anchor = { click: () => calls.push("download"), remove: () => {} };
      global.document = {
        createElement: () => anchor,
        body: { appendChild: () => {} },
      };
      URL.revokeObjectURL = () => {};
      await saveQrImage("data:image/gif;base64,SOURCE", "skygo-ABC123");
      if (os === "web") {
        assert.equal(anchor.download, "skygo-ABC123.png");
        assert.equal(anchor.href, "data:image/png;base64,PNG_BYTES");
        assert.ok(calls.includes("download"));
      } else assert.ok(calls.includes("saved"));
      assert.equal(
        calls.includes("permission"),
        os === "android" && version < 29,
      );
      assert.ok(calls.includes("release-context"));
      assert.ok(calls.includes("release-image"));
    }
  } finally {
    global.document = previousDocument;
    URL.revokeObjectURL = originalRevoke;
    global.__qrSavePlatform.OS = "web";
  }
});

test("denied photo permission never reports a save; render errors still release the context", async () => {
  const { saveQrImage } = require(path.join(dir, "save-qr-image.js"));
  Object.assign(global.__qrSavePlatform, { OS: "android", Version: 28 });
  let released = 0;
  let saves = 0;
  global.__qrSaveMock = {
    manipulate: () => ({
      release: () => released++,
      renderAsync: async () => ({
        release: () => released++,
        saveAsync: async () => ({ uri: "file:///qr.png" }),
      }),
    }),
    permission: async () => ({ granted: false }),
    save: async () => saves++,
  };
  await assert.rejects(saveQrImage("source", "qr"), /cho phép SkyGo lưu ảnh/);
  assert.equal(saves, 0);
  assert.equal(released, 2);
  global.__qrSaveMock.manipulate = () => ({
    release: () => released++,
    renderAsync: async () => {
      throw Error("bad image");
    },
  });
  await assert.rejects(saveQrImage("bad", "qr"), /bad image/);
  assert.equal(released, 3);
  global.__qrSavePlatform.OS = "web";
});

test("booking labels keep pending, rehold, closed and reconciliation distinct from confirmed tickets", () => {
  const { bookingPresentation, flightDate } = require(
    path.join(dir, "booking-presentation.js"),
  );
  assert.equal(bookingPresentation(base, 0, 0).key, "pending");
  assert.equal(
    bookingPresentation({ ...base, bookingStatus: "PAID" }, 0, 0).key,
    "confirming",
  );
  assert.equal(
    bookingPresentation(
      { ...base, bookingStatus: "PAID", payment: { status: "SUCCEEDED" } },
      0,
      0,
    ).key,
    "paid",
  );
  assert.equal(
    bookingPresentation(
      { ...base, bookingStatus: "AWAITING_REHOLD", requiresRehold: true },
      0,
      0,
    ).key,
    "rehold",
  );
  assert.equal(
    bookingPresentation({ ...base, bookingStatus: "CANCELLED" }, 0, 0).key,
    "cancelled",
  );
  assert.equal(
    bookingPresentation({ ...base, bookingClosesAt: base.serverTime }, 0, 0)
      .key,
    "closed",
  );
  assert.equal(
    bookingPresentation(
      { ...base, payment: { status: "RECONCILIATION_REQUIRED" } },
      0,
      0,
    ).key,
    "reconciliation",
  );
  assert.equal(flightDate("2026-11-09T00:30:00+14:00"), "09/11/2026");
});

test("history recovers upcoming booking on a new device and reads its authenticated details", async () => {
  const { loadBookingHistory } = require(path.join(dir, "booking-history.js"));
  const values = new Map();
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  };
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({ url, ...options });
    assert.equal(options.headers.Authorization, "Bearer token");
    return new Response(
      JSON.stringify({
        data: url.endsWith("/home")
          ? { upcomingFlight: { bookingId: "from-home" } }
          : {
              ...base,
              bookingId: "from-home",
              createdAt: "2026-11-08T10:00:00Z",
            },
      }),
    );
  };
  const result = await loadBookingHistory("token", "owner-a", storage);
  assert.equal(result.items.length, 1);
  assert.equal(result.items[0].data.bookingId, "from-home");
  assert.equal(result.failed, false);
  assert.ok(calls[1].url.endsWith("/bookings/from-home"));
  assert.deepEqual(
    require(path.join(dir, "known-bookings.js")).knownBookingIds(
      "owner-a",
      storage,
    ),
    ["from-home"],
  );
  assert.deepEqual(
    require(path.join(dir, "known-bookings.js")).knownBookingIds(
      "owner-b",
      storage,
    ),
    [],
  );
});

test("partial history errors preserve loaded orders and show a retry state", async () => {
  const { loadBookingHistory } = require(path.join(dir, "booking-history.js"));
  const storage = {
    getItem: () => '["good","unavailable"]',
    setItem: () => {},
  };
  global.fetch = async (url) => {
    if (url.endsWith("/home")) throw new TypeError("home unavailable");
    if (url.endsWith("/unavailable"))
      return new Response(
        JSON.stringify({
          error: { code: "HTTP_ERROR", message: "Unavailable" },
        }),
        { status: 503 },
      );
    return new Response(
      JSON.stringify({
        data: { ...base, bookingId: "good", createdAt: base.serverTime },
      }),
    );
  };
  const result = await loadBookingHistory("token", "owner", storage);
  assert.equal(result.items.length, 1);
  assert.equal(result.items[0].data.bookingId, "good");
  assert.equal(result.failed, true);
});

test("history on a new device reports network failure instead of an empty account", async () => {
  const { loadBookingHistory } = require(path.join(dir, "booking-history.js"));
  global.fetch = async () => {
    throw new TypeError("offline");
  };
  const result = await loadBookingHistory("token", "new-owner", {
    getItem: () => null,
    setItem: () => {},
  });
  assert.equal(result.items.length, 0);
  assert.equal(result.failed, true);
});
