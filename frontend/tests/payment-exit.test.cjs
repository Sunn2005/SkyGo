const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const {
  StackRouter,
  StackActions,
} = require("expo-router/build/react-navigation/routers/StackRouter");

function loadModule(file, imports) {
  const source = fs.readFileSync(
    path.join(__dirname, "../src/modules/payment/services", file + ".ts"),
    "utf8",
  );
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
  const exports = {};
  vm.runInNewContext(compiled, {
    exports,
    require: (name) => {
      if (!(name in imports)) throw new Error("Unexpected dependency: " + name);
      return imports[name];
    },
  });
  return exports;
}
const navigation = loadModule("payment-navigation", {});

// Exercise the actual hook handlers with deterministic React state and Expo's real stack reducer.
function exitHarness(names, destination) {
  const router = StackRouter({});
  const options = { routeGetIdList: {}, routeParamList: {} };
  let stack = {
    stale: false,
    type: "stack",
    key: "test-stack",
    index: names.length - 1,
    routeNames: [
      "index",
      "search",
      "results",
      "traveller",
      "payment",
      "payos",
      "bookings",
      "booking",
      "success",
    ],
    routes: names.map((name, i) => ({ key: `${name}-${i}`, name })),
  };
  let state = [],
    cursor = 0,
    prevention,
    disabled = false;
  let paid = false,
    busy = false;
  const calls = [];
  function dispatch(action) {
    const result = router.getStateForAction(stack, action, options);
    assert.ok(result.state, "Expected stack action to be handled");
    stack = result.state;
  }
  const hook = loadModule("use-payment-exit", {
    "./payment-navigation": navigation,
    react: {
      useState: (initial) => {
        const slot = cursor++;
        if (!(slot in state)) state[slot] = initial;
        return [
          state[slot],
          (value) => {
            state[slot] = value;
          },
        ];
      },
    },
    "expo-router": {
      useIsFocused: () => true,
      usePreventRemove: (enabled, callback) => {
        prevention = { enabled, callback };
        return () => {
          disabled = true;
          calls.push("disable");
        };
      },
      router: {
        dismissTo: (href) => {
          assert.equal(
            disabled,
            true,
            "Disable prevention before changing the target",
          );
          calls.push(href);
          dispatch(StackActions.popTo(href.slice(1)));
        },
      },
    },
  });
  const render = () => {
    cursor = 0;
    return hook.usePaymentExit(destination, paid, busy);
  };
  return {
    render,
    calls,
    names: () => stack.routes.map((r) => r.name),
    keys: () => stack.routes.map((r) => r.key),
    back: (type) => {
      assert.equal(prevention.enabled, true);
      let repeated = false;
      prevention.callback({
        data: { action: { type } },
        repeat: () => {
          repeated = true;
        },
      });
      return repeated;
    },
    setPaid: (value) => {
      paid = value;
    },
    setBusy: (value) => {
      busy = value;
    },
    previous: () => dispatch({ type: "GO_BACK" }),
  };
}

test("checkout Back asks first; stay preserves QR and confirm removes form/review above results", () => {
  const h = exitHarness(
    ["index", "search", "results", "traveller", "payment", "payos"],
    "results",
  );
  h.render().requestBack();
  assert.equal(h.render().visible, true);
  assert.equal(h.names().at(-1), "payos");
  h.render().stay();
  assert.equal(h.render().visible, false);
  assert.deepEqual(h.calls, []);
  h.render().requestBack();
  h.render().leave();
  assert.deepEqual(h.names(), ["index", "search", "results"]);
  h.previous();
  assert.deepEqual(h.names(), ["index", "search"]);
});

test("history QR returns to the original detail once, then Back goes straight to history", () => {
  const h = exitHarness(["index", "bookings", "booking", "payos"], "booking");
  const originalDetail = h.keys()[2];
  h.render().requestBack();
  h.render().leave();
  assert.deepEqual(h.names(), ["index", "bookings", "booking"]);
  assert.equal(h.keys()[2], originalDetail);
  h.previous();
  assert.deepEqual(h.names(), ["index", "bookings"]);
});

test("hardware Back and swipe pops show the same dialog without repeating the original action", () => {
  for (const type of ["GO_BACK", "POP", "POP_TO", "POP_TO_TOP"]) {
    const h = exitHarness(["bookings", "booking", "payos"], "booking");
    h.render();
    assert.equal(h.back(type), false);
    assert.equal(h.render().visible, true);
    assert.equal(h.names().at(-1), "payos");
    h.render().leave();
    assert.deepEqual(h.names(), ["bookings", "booking"]);
  }
});

test("busy mutation cannot be left; payment confirmation closes the dialog and allows success", () => {
  const h = exitHarness(["results", "payment", "payos"], "results");
  h.setBusy(true);
  h.render().requestBack();
  h.render().leave();
  assert.equal(h.names().at(-1), "payos");
  h.setBusy(false);
  h.render();
  assert.equal(h.back("REPLACE"), true);
  h.setPaid(true);
  assert.equal(h.render().visible, false);
});

test("cold QR falls back to one detail; only known return destinations are accepted", () => {
  assert.equal(navigation.paymentReturnTo("results"), "results");
  assert.equal(navigation.paymentReturnTo(undefined, "results"), "results");
  assert.equal(
    navigation.paymentReturnTo("https://example.com", "booking"),
    "booking",
  );
  assert.equal(navigation.paymentReturnTo(), "booking");
  const h = exitHarness(["payos"], "booking");
  h.render().requestBack();
  h.render().leave();
  assert.deepEqual(h.names(), ["booking"]);
});
