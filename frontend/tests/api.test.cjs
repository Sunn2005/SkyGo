const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const ts = require("typescript");
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "skygo-api-"));
for (const [source, target] of [
  ["shared/api/client", "client"],
  ["shared/utils/date-wheel", "date-wheel"],
  ["shared/utils/date", "date"],
  ["modules/account/types", "types"],
  ["modules/account/utils/login-destination", "login-destination"],
  ["modules/flights/data/airports", "airports"],
  ["modules/flights/services/flight-service", "flights"],
  ["modules/flights/services/nearby-days", "nearby-days"],
  ["modules/account/data/api-account-service", "account"],
  ["modules/booking/data/api-booking-service", "booking-api"],
  ["modules/booking/services/live-selection", "live-selection"],
  ["modules/booking/services/saved-passenger-service", "saved-passengers"],
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
    .replace('"@/shared/utils/date"', '"./date"')
    .replace('"../types"', '"./types"')
    .replace('"../data/airports"', '"./airports"')
    .replace('"../data/api-booking-service"', '"./booking-api"');
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

test("destination picker loads international catalog across pages without origin route filter", async () => {
  const calls = [];
  global.fetch = async (url) => {
    const query = new URL(url).searchParams;
    calls.push(query);
    assert.equal(query.has("origin"), false);
    const page = Number(query.get("page"));
    return new Response(
      JSON.stringify({
        data:
          page === 0
            ? [
                { code: "SGN", regionCode: "VN" },
                { code: "HAN", regionCode: "VN" },
              ]
            : [{ code: "SIN", regionCode: "SEA" }],
        pagination: { page, totalPages: 2 },
      }),
    );
  };
  const items = await require(path.join(dir, "flights.js")).loadPickerAirports(
    "",
    "SGN",
  );
  assert.deepEqual(
    items.map((item) => item.code),
    ["HAN", "SIN"],
  );
  assert.equal(calls.length, 2);
});

test("real booking adapter sends hold/review/baggage and idempotent creation contracts", async () => {
  const { bookingApi, quoteRequest, passengerRequest } = require(
    path.join(dir, "booking-api.js"),
  );
  const search = {
    type: "round-trip",
    count: 1,
    children: 1,
    infants: 1,
    cabin: "Phổ thông",
  };
  const selected = [{ id: "out", cabin: "Thương gia" }, { id: "back" }];
  const quote = quoteRequest("hold-1", search, selected);
  assert.equal(quote.tripType, "ROUND_TRIP");
  assert.deepEqual(quote.selections, [
    { direction: "OUTBOUND", flightId: "out", cabinClass: "BUSINESS" },
    { direction: "RETURN", flightId: "back", cabinClass: "ECONOMY" },
  ]);
  const passenger = passengerRequest(
    {
      kind: "adult",
      title: "Ông",
      firstName: " An ",
      lastName: " Nguyen ",
      birthDate: "01/02/1990",
      gender: "Nam",
      documentType: "passport",
      documentNumber: " B1234567 ",
      nationality: "vn",
      issuingCountry: "vn",
      documentExpiry: "31/12/2030",
    },
    0,
  );
  assert.equal(passenger.dateOfBirth, "1990-02-01");
  assert.equal(passenger.document.expiresOn, "2030-12-31");
  assert.equal(passenger.document.issuingCountryCode, "VN");
  assert.equal(passenger.clientPassengerId, "p1");
  const personalId = passengerRequest(
    {
      ...{
        firstName: "An",
        lastName: "Nguyen",
        birthDate: "01/02/1990",
        gender: "Nam",
      },
      documentType: "cccd",
      documentNumber: "123456789012",
    },
    0,
  );
  assert.equal(personalId.document.nationalityCountryCode, null);
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({
      url,
      ...options,
      body: options.body ? JSON.parse(options.body) : null,
    });
    return new Response(JSON.stringify({ data: { bookingId: "booking-1" } }));
  };
  await bookingApi.hold("token", search, selected[0]);
  await bookingApi.leg("token", "hold-1", search, selected[1], 1);
  await bookingApi.review("token", {
    ...quote,
    passengers: [passenger],
    contact: { email: "a@example.com", phoneNumber: "0912345678" },
  });
  await bookingApi.baggage("token", "review-1", 2, [
    { clientPassengerId: "p1", direction: "OUTBOUND", extraPieces: 2 },
  ]);
  await bookingApi.create(
    "token",
    { reviewId: "review-1", version: 3 },
    "stable-retry-key",
  );
  await bookingApi.create(
    "token",
    { reviewId: "review-1", version: 3 },
    "stable-retry-key",
  );
  assert.ok(
    calls.every((call) => call.headers.Authorization === "Bearer token"),
  );
  assert.equal(calls[0].body.selection.direction, "OUTBOUND");
  assert.ok(calls[1].url.endsWith("/legs/RETURN"));
  assert.equal(calls[1].method, "PUT");
  assert.equal(calls[3].body.expectedVersion, 2);
  assert.deepEqual(calls[4].body, { reviewId: "review-1", expectedVersion: 3 });
  assert.equal(
    calls[4].headers["Idempotency-Key"],
    calls[5].headers["Idempotency-Key"],
  );
  assert.ok(calls.every((call) => !call.url.includes("payos")));
});

test("login resumes flight selection and accepts existing result return paths", () => {
  const { loginDestination } = require(path.join(dir, "login-destination.js"));
  for (const value of ["results", "/results"])
    assert.equal(loginDestination(value).href, "/results");
  for (const value of ["flight-selection", "/flight-selection", "detail"])
    assert.deepEqual(loginDestination(value), {
      href: "/results",
      resumeFlight: true,
    });
  for (const value of [
    undefined,
    "https://example.com",
    "//example.com",
    "unknown",
  ])
    assert.deepEqual(loginDestination(value), {
      href: "/",
      resumeFlight: false,
    });
});

test("review configuration failure stays an API error and never becomes a mock review", async () => {
  const { bookingApi } = require(path.join(dir, "booking-api.js"));
  const { ApiError } = require(path.join(dir, "client.js"));
  global.fetch = async () =>
    new Response(
      JSON.stringify({
        error: {
          code: "REVIEW_ENCRYPTION_UNAVAILABLE",
          message: "Backend chưa được cấu hình khóa mã hóa review hợp lệ.",
        },
      }),
      { status: 503 },
    );
  await assert.rejects(bookingApi.review("token", {}), (error) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 503);
    assert.equal(error.code, "REVIEW_ENCRYPTION_UNAVAILABLE");
    return true;
  });
});

const selectionSearch = {
  type: "one-way",
  from: "SGN",
  to: "HAN",
  date: "2026-11-21",
  secondDate: "2026-11-24",
  count: 2,
  cabin: "Phổ thông",
};
const selectedFlight = { id: "new-flight", cabin: "Phổ thông" };
const currentHold = {
  seatHoldId: "old-hold",
  version: 4,
  bookingId: "saved-order",
  expiresAt: "2026-11-21T10:15:00Z",
};
const { createLiveSelection, HoldConfirmationRequired } = require(
  path.join(dir, "live-selection.js"),
);
const reply = (data) => new Response(JSON.stringify({ data }));

test("atomic selection changes trip type and counts in one request with hold version", async () => {
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return reply(
      calls.length === 1
        ? { ...currentHold, bookingId: null, tripType: "ROUND_TRIP" }
        : { ...currentHold, version: 5, bookingId: null },
    );
  };
  const result = await createLiveSelection().select(
    "token",
    selectionSearch,
    [selectedFlight],
    () => {},
  );
  assert.equal(result.version, 5);
  assert.equal(calls.length, 2);
  assert.ok(calls[1].url.endsWith("/old-hold/selection"));
  assert.equal(calls[1].method, "PUT");
  assert.deepEqual(JSON.parse(calls[1].body), {
    expectedVersion: 4,
    tripType: "ONE_WAY",
    passengerCounts: { adults: 2, children: 0, infants: 0 },
    selections: [
      { direction: "OUTBOUND", flightId: "new-flight", cabinClass: "ECONOMY" },
    ],
  });
});

test("booked hold requires confirmation and a stale confirmation never closes QR", async () => {
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return reply(currentHold);
  };
  const manager = createLiveSelection();
  for (const confirmation of [
    undefined,
    { seatHoldId: "old-hold", version: 3 },
    { seatHoldId: "other", version: 4 },
  ]) {
    await assert.rejects(
      manager.select(
        "token",
        selectionSearch,
        [selectedFlight],
        () => {},
        confirmation,
      ),
      (error) => {
        assert.ok(error instanceof HoldConfirmationRequired);
        assert.equal(error.hold.seatHoldId, "old-hold");
        assert.equal(error.hold.version, 4);
        assert.equal(error.hold.bookingId, "saved-order");
        return true;
      },
    );
  }
  assert.ok(calls.every((call) => call.method === "GET"));
});

test("lost switch response retries identical key/body without mutating a different selection", async () => {
  const calls = [];
  let switchCalls = 0;
  global.fetch = async (url, options) => {
    calls.push({ url, ...options });
    if (url.endsWith("/current")) return reply(currentHold);
    if (++switchCalls === 1) throw new TypeError("response lost after commit");
    return reply({
      switchId: "switch-1",
      seatHold: {
        ...currentHold,
        seatHoldId: "target",
        bookingId: null,
        version: 1,
      },
    });
  };
  const manager = createLiveSelection();
  const confirmation = { seatHoldId: "old-hold", version: 4 };
  await assert.rejects(
    manager.select(
      "token",
      selectionSearch,
      [selectedFlight],
      () => {},
      confirmation,
    ),
    /Không kết nối/,
  );
  await assert.rejects(
    manager.select("token", selectionSearch, [{ id: "other" }], () => {}),
    /Lần đổi trước/,
  );
  const hold = await manager.select(
    "token",
    selectionSearch,
    [selectedFlight],
    () => {},
  );
  assert.equal(hold.seatHoldId, "target");
  const writes = calls.filter((call) => call.method === "POST");
  assert.equal(writes.length, 2);
  assert.equal(
    writes[0].headers["Idempotency-Key"],
    writes[1].headers["Idempotency-Key"],
  );
  assert.equal(writes[0].body, writes[1].body);
  assert.equal(JSON.parse(writes[0].body).expectedVersion, 4);
});

test("changed session blocks a mutation after current hold was read", async () => {
  let writes = 0;
  global.fetch = async (_, options) => {
    if (options.method !== "GET") writes++;
    return reply(null);
  };
  await assert.rejects(
    createLiveSelection().select(
      "token",
      selectionSearch,
      [selectedFlight],
      () => {
        throw Error("session changed");
      },
    ),
    /session changed/,
  );
  assert.equal(writes, 0);
});

test("nearby search preserves disabled/empty days and return route; load more uses chosen day and size 10", async () => {
  const { searchNearbyFlights } = require(path.join(dir, "flights.js"));
  const search = { ...selectionSearch, type: "round-trip" };
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({ url, body: JSON.parse(options.body) });
    if (url.endsWith("nearby-days"))
      return reply({
        centerDate: search.secondDate,
        days: [
          {
            date: "2026-11-21",
            selectable: false,
            disabledReason: "RETURN_NOT_AFTER_DEPARTURE",
            flights: [],
            pagination: {
              page: 0,
              size: 10,
              totalElements: 0,
              totalPages: 0,
              hasMore: false,
            },
          },
          {
            date: "2026-11-24",
            selectable: true,
            disabledReason: null,
            flights: [sampleFlight],
            pagination: {
              page: 0,
              size: 10,
              totalElements: 11,
              totalPages: 2,
              hasMore: true,
            },
          },
        ],
      });
    return new Response(
      JSON.stringify({
        data: [],
        pagination: { page: 1, size: 10, totalPages: 2 },
      }),
    );
  };
  const result = await searchNearbyFlights(search, "RETURN");
  assert.equal(result.days[0].selectable, false);
  assert.equal(result.days[0].disabledReason, "RETURN_NOT_AFTER_DEPARTURE");
  assert.equal(result.days[1].flights[0].id, sampleFlight.flightId);
  assert.equal(result.days[1].pagination.hasMore, true);
  assert.equal(calls[0].body.origin, "SGN");
  assert.equal(calls[0].body.destination, "HAN");
  assert.equal(calls[0].body.direction, "RETURN");
  assert.equal(Object.hasOwn(calls[0].body, "page"), false);
  await searchFlights(
    { ...search, secondDate: "2026-11-25" },
    "RETURN",
    1,
    undefined,
    10,
  );
  assert.equal(calls[1].body.departureDate, search.date);
  assert.equal(calls[1].body.returnDate, "2026-11-25");
  assert.equal(calls[1].body.size, 10);
});

test("version conflict reloads current hold and requires fresh confirmation before replacement", async () => {
  let version = 4;
  let writes = 0;
  global.fetch = async (url, options) => {
    if (url.endsWith("/current"))
      return reply({ ...currentHold, bookingId: null, version });
    writes++;
    if (writes === 1) {
      version = 5;
      return new Response(
        JSON.stringify({
          error: { code: "SEAT_HOLD_VERSION_CONFLICT", message: "changed" },
        }),
        { status: 409 },
      );
    }
    assert.equal(JSON.parse(options.body).expectedVersion, 5);
    return reply({ ...currentHold, bookingId: null, version: 6 });
  };
  const manager = createLiveSelection();
  let confirmation;
  await assert.rejects(
    manager.select("token", selectionSearch, [selectedFlight], () => {}),
    (error) => {
      assert.ok(error instanceof HoldConfirmationRequired);
      confirmation = error.hold;
      assert.equal(confirmation.version, 5);
      return true;
    },
  );
  assert.equal(writes, 1);
  await assert.rejects(
    manager.select("token", selectionSearch, [selectedFlight], () => {}, {
      ...confirmation,
      version: 4,
    }),
    HoldConfirmationRequired,
  );
  assert.equal(writes, 1);
  const result = await manager.select(
    "token",
    selectionSearch,
    [selectedFlight],
    () => {},
    confirmation,
  );
  assert.equal(result.version, 6);
  assert.equal(writes, 2);
});

test("PayOS callback login destinations survive sign-in and reject arbitrary redirect targets", () => {
  const { loginDestination } = require(path.join(dir, "login-destination.js"));
  for (const route of ["payment/success", "payment/cancel"]) {
    for (const value of [route, "/" + route])
      assert.deepEqual(loginDestination(value), {
        href: "/" + route,
        resumeFlight: false,
      });
  }
  for (const value of [
    "payment/success?status=PAID",
    "//payment/success",
    "https://example.com/payment/success",
    "payment/../success",
  ])
    assert.equal(loginDestination(value).href, "/");
});

test("scrolling date windows preserves round-trip bounds and the original route", () => {
  const { nearbyWindowSearch, shiftDate } = require(
    path.join(dir, "nearby-days.js"),
  );
  const search = {
    ...selectionSearch,
    type: "round-trip",
    date: "2026-11-10",
    secondDate: "2026-11-20",
  };
  const outbound = nearbyWindowSearch(search, "OUTBOUND", "2026-11-18", "next");
  assert.equal(outbound.date, "2026-11-19");
  assert.equal(outbound.secondDate, "2026-11-20");
  const back = nearbyWindowSearch(search, "RETURN", "2026-11-23", "next");
  assert.equal(back.secondDate, "2026-11-27");
  assert.equal(back.date, search.date);
  assert.equal(back.from, search.from);
  assert.equal(back.to, search.to);
  const earlier = nearbyWindowSearch(
    search,
    "OUTBOUND",
    "2026-11-09",
    "previous",
  );
  assert.equal(earlier.date, "2026-11-09");
  assert.equal(shiftDate("2026-12-30", 4), "2027-01-03");
  assert.equal(shiftDate("2028-03-01", -1), "2028-02-29");
});

test("overlapping date batches keep previously loaded flight pages and distinct dates sorted", () => {
  const { mergeNearbyDays } = require(path.join(dir, "nearby-days.js"));
  const loaded = {
    date: "2026-11-10",
    flights: [{ id: "first" }, { id: "second-page" }],
    pagination: { page: 1 },
  };
  const current = [loaded, { date: "2026-11-11", flights: [] }];
  const next = [
    { date: "2026-11-10", flights: [{ id: "first" }], pagination: { page: 0 } },
    { date: "2026-11-09", flights: [] },
    { date: "2026-11-12", flights: [] },
  ];
  const result = mergeNearbyDays(current, next);
  assert.deepEqual(
    result.map((day) => day.date),
    ["2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12"],
  );
  assert.equal(result[1], loaded);
  assert.equal(result[1].pagination.page, 1);
  assert.equal(result[1].flights.length, 2);
  assert.equal(current.length, 2);
});

test("birth date wheel clamps leap days, short months and future dates", () => {
  const { changeDatePart, dateWheelRanges } = require(
    path.join(dir, "date-wheel.js"),
  );
  const min = "1900-01-01",
    max = "2026-11-08";
  assert.equal(
    changeDatePart("2024-02-29", "year", 2023, min, max),
    "2023-02-28",
  );
  assert.equal(
    changeDatePart("2024-01-31", "month", 2, min, max),
    "2024-02-29",
  );
  assert.equal(changeDatePart("2026-11-08", "month", 12, min, max), max);
  assert.equal(changeDatePart("1900-01-01", "year", 1899, min, max), min);
  const current = dateWheelRanges(max, min, max);
  assert.equal(current.year[0], 2026);
  assert.equal(current.year.at(-1), 1900);
  assert.equal(current.month.at(-1), 11);
  assert.equal(current.day.at(-1), 8);
  assert.equal(dateWheelRanges("2024-02-29", min, max).day.length, 29);
  assert.equal(dateWheelRanges("2023-02-28", min, max).day.length, 28);
});

test("saved passenger API round trips profile fields and uses authenticated CRUD", async () => {
  const { savedPassengerApi, profileInput, profileTraveller } = require(
    path.join(dir, "saved-passengers.js"),
  );
  const profile = {
    savedPassengerId: "profile/one",
    title: "MR",
    givenNames: "Van An",
    surname: "Nguyen",
    dateOfBirth: "1990-02-03",
    gender: "MALE",
    document: {
      documentType: "PASSPORT",
      number: "A1234567",
      issuingCountryCode: "VN",
      nationalityCountryCode: "VN",
      expiresOn: "2035-04-05",
    },
  };
  const input = profileInput(profileTraveller(profile));
  const { savedPassengerId, ...expected } = profile;
  assert.deepEqual(input, expected);
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return options.method === "DELETE"
      ? new Response(null, { status: 204 })
      : new Response(
          JSON.stringify({
            data:
              options.method === "GET" && url.endsWith("saved-passengers")
                ? []
                : profile,
          }),
        );
  };
  await savedPassengerApi.list("owner-token");
  await savedPassengerApi.get("owner-token", savedPassengerId);
  await savedPassengerApi.create("owner-token", input);
  await savedPassengerApi.update("owner-token", savedPassengerId, input);
  await savedPassengerApi.remove("owner-token", savedPassengerId);
  assert.ok(calls[1].url.endsWith("/saved-passengers/profile%2Fone"));
  assert.deepEqual(
    calls.map((c) => c.method),
    ["GET", "GET", "POST", "PUT", "DELETE"],
  );
  assert.ok(
    calls.every((c) => c.headers.Authorization === "Bearer owner-token"),
  );
  assert.equal(JSON.parse(calls[2].body).userId, undefined);
  assert.equal(JSON.parse(calls[3].body).savedPassengerId, undefined);
});

test("saved profile prefill preserves trip-specific choices and never uses CCCD as a passport", () => {
  const {
    applySavedPassenger,
    passengerKindOnDate,
    profileInput,
    profileErrors,
  } = require(path.join(dir, "saved-passengers.js"));
  const profile = {
    savedPassengerId: "child",
    title: null,
    givenNames: "An",
    surname: "Nguyen",
    dateOfBirth: "2020-11-21",
    gender: "FEMALE",
    document: {
      documentType: "PERSONAL_ID",
      number: "000012345678",
      issuingCountryCode: "VN",
      nationalityCountryCode: null,
      expiresOn: null,
    },
  };
  const current = { kind: "child", companionIndex: 0, extraBags: { 0: 2 } };
  const domestic = applySavedPassenger(profile, current, true);
  assert.equal(domestic.kind, "child");
  assert.equal(domestic.companionIndex, 0);
  assert.deepEqual(domestic.extraBags, { 0: 2 });
  assert.equal(domestic.title, undefined);
  assert.equal(profileInput(domestic).document.number, "000012345678");
  assert.deepEqual(profileErrors(domestic), {});
  const international = applySavedPassenger(profile, current, false);
  assert.equal(international.documentType, "passport");
  assert.equal(international.documentNumber, "");
  assert.ok(profileErrors(international).documentNumber);
  assert.equal(passengerKindOnDate("2014-11-21", "2026-11-20"), "child");
  assert.equal(passengerKindOnDate("2014-11-21", "2026-11-21"), "adult");
  assert.equal(passengerKindOnDate("2024-11-21", "2026-11-20"), "infant");
  assert.equal(passengerKindOnDate("2024-11-21", "2026-11-21"), "child");
  domestic.firstName = "Changed for this trip";
  assert.equal(profile.givenNames, "An");
});
