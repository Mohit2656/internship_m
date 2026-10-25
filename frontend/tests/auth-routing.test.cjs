const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
function load(file, imports) {
  const module = { exports: {} };
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText;
  new Function("require", "module", "exports", code)(name => imports[name] || require(name), module, module.exports);
  return module.exports;
}
function nodes(tree, type) {
  const found = [];
  function visit(node) { if (!node || typeof node !== "object") return; if (node.type === type) found.push(node); React.Children.forEach(node.props?.children, visit); }
  visit(tree); return found;
}
for (const [role, destination] of [["STUDENT", "/student/dashboard"], ["MENTOR", "/mentor/dashboard"], ["ADMIN", "/admin/dashboard"]]) {
  test(`${role} login submits the supported role and uses the authenticated response destination`, async () => {
    let cursor = 0; const slots = ["student@example.edu", "fixture-password", false, ""]; const calls = [], redirects = [];
    const { default: Page } = load("app/login/page.tsx", {
      react: { Suspense: React.Suspense, useEffect() {}, useState(initial) { const index = cursor++; return [index in slots ? slots[index] : initial, value => { slots[index] = value; }]; } },
      "next/link": { default: () => null }, "next/image": { default: () => null },
      "@/components/ThemeToggle": { ThemeToggle: () => null },
      "next/navigation": { usePathname: () => "/login", useSearchParams: () => ({ get: () => role }), useRouter: () => ({ replace: url => redirects.push(url) }) },
      "@/lib/auth": { useAuth: () => ({ user: null, loading: false, refresh: async () => null }) },
      "@/lib/api": { api: { post: async (url, body) => { calls.push([url, body]); return { data: { role, mustChangePassword: false } }; } } }
    });
    const content = Page().props.children;
    const tree = content.type();
    assert.equal(nodes(tree, "form").length, 1);
    await nodes(tree, "form")[0].props.onSubmit({ preventDefault() {} });
    assert.deepEqual(calls, [["/auth/login", { email: "student@example.edu", password: "fixture-password", role }]]);
    assert.deepEqual(redirects, [destination]);
  });
}
function protectedView(user, role = "STUDENT", pathname = "/student/dashboard") {
  const effects = [], redirects = [];
  const { ProtectedRoute } = load("components/ProtectedRoute.tsx", {
    react: { useEffect: effect => effects.push(effect) },
    "@/lib/auth": { useAuth: () => ({ user, loading: false }) },
    "next/navigation": { usePathname: () => pathname, useRouter: () => ({ replace: url => redirects.push(url) }) }
  });
  const html = renderToStaticMarkup(ProtectedRoute({ role, children: React.createElement("p", {}, "protected-content") }));
  effects.forEach(effect => effect());
  return { html, redirects };
}
test("unauthenticated or wrong-role users cannot render protected content", () => {
  const missing = protectedView(null); assert.doesNotMatch(missing.html, /protected-content/); assert.deepEqual(missing.redirects, ["/login"]);
  const mentor = protectedView({ role: "MENTOR", mustChangePassword: false });
  assert.doesNotMatch(mentor.html, /protected-content/); assert.deepEqual(mentor.redirects, ["/mentor/dashboard"]);
});
test("forced password changes protect both student and mentor dashboards", () => {
  for (const role of ["STUDENT", "MENTOR"]) {
    const base = role.toLowerCase(); const user = { role, mustChangePassword: true };
    const blocked = protectedView(user, role, `/${base}/dashboard`);
    assert.doesNotMatch(blocked.html, /protected-content/); assert.deepEqual(blocked.redirects, [`/${base}/change-password`]);
    assert.match(protectedView(user, role, `/${base}/change-password`).html, /protected-content/);
  }
});
test("normal authenticated navigation preserves authorized content", () => {
  assert.match(protectedView({ role: "STUDENT", mustChangePassword: false }).html, /protected-content/);
});
