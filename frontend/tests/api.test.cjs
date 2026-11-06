const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const ts = require("typescript");
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "skygo-api-"));
for (const [source, target] of [
  ["shared/api/client", "client"],
  ["modules/account/types", "types"],
  ["modules/flights/data/airports", "airports"],
  ["modules/flights/services/flight-service", "flights"],
  ["modules/account/data/api-account-service", "account"],
]) {
  let text = fs.readFileSync(
    path.join(__dirname, "../src", source + ".ts"),
    "utf8",
  );
  text = text
    .replace(
      'import { Platform } from "react-native";',
      'const Platform = { OS: "web" };',
    )
    .replace('"@/shared/api/client"', '"./client"')
    .replace('"../types"', '"./types"')
    .replace('"../data/airports"', '"./airports"');
  fs.writeFileSync(
    path.join(dir, target + ".js"),
    ts.transpileModule(text, {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
      },
    }).outputText,
  );
}
const originalFetch = global.fetch;
after(() => {
  global.fetch = originalFetch;
  fs.rmSync(dir, { recursive: true, force: true });
});
const { accountService } = require(path.join(dir, "account.js"));
const { AccountError } = require(path.join(dir, "types.js"));
const user = {
  userId: "usr_123",
  fullName: "Test User",
  phoneNumber: "+84912345678",
  email: "test@example.com",
};
test("API lifecycle maps IDs, sends JSON and Bearer token, handles empty logout", async () => {
  const calls = [];
  const replies = [
    new Response(JSON.stringify({ data: user }), { status: 201 }),
    new Response(
      JSON.stringify({
        data: {
          accessToken: "real-token",
          expiresAt: "2030-01-01T00:00:00Z",
          user,
        },
      }),
    ),
    new Response(JSON.stringify({ data: user })),
    new Response(null, { status: 204 }),
  ];
  global.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return replies.shift();
  };
  assert.equal(
    (await accountService.register({ fullName: "Test User" })).id,
    "usr_123",
  );
  const session = await accountService.login({
    phoneNumber: "0912345678",
    password: "ExamplePass1!",
  });
  assert.equal(session.user.id, "usr_123");
  assert.equal((await accountService.me(session.accessToken)).id, "usr_123");
  await accountService.logout(session.accessToken);
  assert.ok(calls[1].url.endsWith("/api/v1/auth/login"));
  assert.equal(JSON.parse(calls[1].body).phoneNumber, "0912345678");
  assert.equal(calls[1].headers["Content-Type"], "application/json");
  assert.equal(calls[2].headers.Authorization, "Bearer real-token");
  assert.equal(calls[3].method, "POST");
});
test("API errors preserve validation fields, lock expiry and authentication code", async () => {
  for (const [status, code, details, lockedUntil] of [
    [422, "VALIDATION_ERROR", [{ field: "password", message: "Too short" }]],
    [429, "LOGIN_TEMPORARILY_LOCKED", [], "2030-01-01T00:00:00Z"],
    [401, "UNAUTHENTICATED", []],
  ]) {
    global.fetch = async () =>
      new Response(
        JSON.stringify({
          error: { code, message: "Error", details, lockedUntil },
        }),
        { status },
      );
    await assert.rejects(accountService.me("token"), (error) => {
      assert.ok(error instanceof AccountError);
      assert.equal(error.code, code);
      if (status === 422) assert.equal(error.fields.password, "Too short");
      if (status === 429) assert.equal(error.retryAt, Date.parse(lockedUntil));
      return true;
    });
  }
});
test("network failure has a useful message", async () => {
  global.fetch = async () => {
    throw new TypeError("fetch failed");
  };
  await assert.rejects(
    accountService.me("token"),
    /Không kết nối được máy chủ/,
  );
});

const { loadRegions, loadAirports, searchFlights } = require(
  path.join(dir, "flights.js"),
);
const sampleFlight = {
  flightId: "real-flight",
  flightNumber: "SG101",
  airline: { code: "SG", name: "SkyGo" },
  origin: { code: "SGN", city: "Hồ Chí Minh" },
  destination: { code: "HAN", city: "Hà Nội" },
  departureAt: "2026-11-10T23:00:00+07:00",
  arrivalAt: "2026-11-11T01:00:00+07:00",
  durationMinutes: 120,
  fares: [
    {
      cabinClass: "ECONOMY",
      price: { amount: 3564000, currency: "VND", includesVat: true },
    },
  ],
};
test("catalog APIs preserve pagination, encode keyword/origin and support new airport codes", async () => {
  global.fetch = async () =>
    new Response(
      JSON.stringify({
        data: [{ regionCode: "VN", regionName: "Việt Nam", sortOrder: 1 }],
      }),
    );
  assert.equal((await loadRegions())[0].regionCode, "VN");
  global.fetch = async (url) => {
    const query = new URL(url).searchParams;
    assert.equal(query.get("origin"), "SGN");
    assert.equal(query.get("keyword"), "Hà Nội");
    assert.equal(query.get("page"), "2");
    return new Response(
      JSON.stringify({
        data: [
          {
            code: "NEW",
            name: "New Airport",
            city: "New City",
            countryName: "New Country",
            regionCode: "VN",
          },
        ],
        pagination: { page: 2, size: 100, totalElements: 201, totalPages: 3 },
      }),
    );
  };
  const result = await loadAirports({
    origin: "SGN",
    keyword: "Hà Nội",
    page: 2,
  });
  assert.equal(result.pagination.totalPages, 3);
  assert.equal(
    require(path.join(dir, "airports.js")).airport("NEW").city,
    "New City",
  );
});
test("search sends canonical round trip route for RETURN and maps fares without inventing business price", async () => {
  global.fetch = async (url, options) => {
    assert.ok(url.endsWith("/api/v1/flights/search"));
    assert.equal(options.method, "POST");
    const body = JSON.parse(options.body);
    assert.equal(body.origin, "SGN");
    assert.equal(body.destination, "HAN");
    assert.equal(body.direction, "RETURN");
    assert.equal(body.tripType, "ROUND_TRIP");
    assert.equal(body.returnDate, "2026-11-12");
    assert.deepEqual(body.passengerCounts, {
      adults: 2,
      children: 1,
      infants: 1,
    });
    assert.equal(body.page, 1);
    return new Response(
      JSON.stringify({
        data: [sampleFlight],
        pagination: { page: 1, size: 20, totalElements: 21, totalPages: 2 },
      }),
    );
  };
  const result = await searchFlights(
    {
      type: "round-trip",
      from: "SGN",
      to: "HAN",
      date: "2026-11-10",
      secondDate: "2026-11-12",
      count: 2,
      children: 1,
      infants: 1,
      cabin: "Phổ thông",
    },
    "RETURN",
    1,
  );
  assert.equal(result.pagination.totalPages, 2);
  assert.equal(result.data[0].id, "real-flight");
  assert.equal(result.data[0].departure, "23:00");
  assert.equal(result.data[0].arrivalAt, sampleFlight.arrivalAt);
  assert.equal(result.data[0].price, 3564000);
  assert.equal(result.data[0].businessPrice, undefined);
  assert.deepEqual(result.data[0].availableCabins, ["Phổ thông"]);
});
test("one way search omits return date and supports empty result pages", async () => {
  global.fetch = async (_, options) => {
    assert.equal(Object.hasOwn(JSON.parse(options.body), "returnDate"), false);
    return new Response(
      JSON.stringify({
        data: [],
        pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
      }),
    );
  };
  const result = await searchFlights(
    {
      type: "one-way",
      from: "SGN",
      to: "HAN",
      date: "2026-11-10",
      secondDate: "2026-11-12",
      count: 1,
      cabin: "Phổ thông",
    },
    "OUTBOUND",
  );
  assert.deepEqual(result.data, []);
});
