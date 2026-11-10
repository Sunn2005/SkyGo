const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");

test("favorite requests use authenticated API, preserve pagination metadata and encode flight IDs", async () => {
  const calls = [];
  const expected = {
    data: [],
    pagination: { page: 2, size: 20, totalElements: 42, totalPages: 3 },
    favoriteFlightIds: ["abc"],
  };
  const source = fs.readFileSync(
    path.join(__dirname, "../src/modules/flights/services/favorite-service.ts"),
    "utf8",
  );
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText;
  const exports = {};
  vm.runInNewContext(compiled, {
    exports,
    require: () => ({
      apiRequest: async (url, options) => {
        calls.push({ url, options });
        return expected;
      },
    }),
  });
  const api = exports.favoriteApi;
  assert.equal(await api.list("account-token", 2), expected);
  assert.equal(calls[0].url, "/api/v1/favorite-flights?page=2&size=20");
  assert.equal(calls[0].options.unwrap, false);
  await api.save("account-token", "flight/one");
  await api.remove("account-token", "flight/one");
  assert.equal(calls[1].url, "/api/v1/favorite-flights/flight%2Fone");
  assert.equal(calls[1].options.method, "PUT");
  assert.equal(calls[2].options.method, "DELETE");
  for (const call of calls) {
    assert.equal(call.options.token, "account-token");
    assert.equal(call.options.body, undefined);
  }
});
