const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");
const React = require("react"), { renderToStaticMarkup } = require("react-dom/server");
const flush = () => new Promise(resolve => setImmediate(resolve));
function harness(file, name, api) {
  const states = [], effects = [], paths = []; let cursor = 0;
  const hooks = { useState(initial) { const i = cursor++; if (!(i in states)) states[i] = initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; }, useEffect(effect) { if (!effects.length) effects.push(effect); } };
  const m = { exports: {} };
  const imports = { react: hooks, "@/lib/api": { api }, "next/navigation": { useRouter: () => ({ push: url => paths.push(url) }) }, "next/link": { default: ({ children, ...props }) => React.createElement("a", props, children) }, "@/components/AppFrame": { AppFrame: () => null }, "@/components/ProtectedRoute": { ProtectedRoute: () => null }, "@/lib/monthly-progress": { formatDateTime: value => value }, "@/lib/notifications": { NOTIFICATIONS_CHANGED: "changed", syncNotificationCount() {} } };
  new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText)(key => imports[key] || require(key), m, m.exports);
  function tree() { cursor = 0; return m.exports[name](); }
  function find(node, label) { if (!node || typeof node !== "object") return; if (node.type === "button" && node.props.children === label) return node; for (const child of React.Children.toArray(node.props?.children)) { const result = find(child, label); if (result) return result; } }
  return { html: () => renderToStaticMarkup(tree()), paths, button: label => find(tree(), label), async mount() { tree(); const cleanup = effects[0](); await flush(); return cleanup; } };
}
function browser(t) { const previous = global.window; global.window = { setInterval() { return 1; }, clearInterval() {}, addEventListener() {}, removeEventListener() {} }; t.after(() => { if (previous === undefined) delete global.window; else global.window = previous; }); }
const item = { id: 12, type: "REPORT_REJECTED", title: "Month 2 rejected", message: "Correct and resubmit", rejectionReason: "Explain your actual contribution <script>", mentorName: "Faculty A", actionUrl: "/student/monthly-progress#report-7", createdAt: "2026-10-22T07:00:00Z", readAt: null };
test("exact rejection reason is visible and opening marks the selected notification read", async t => {
  browser(t); const calls = [];
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", { async get() { return { data: { content: [item], page: 0, totalPages: 1, unreadCount: 1 } }; }, async patch(url) { calls.push(url); return { data: { unreadCount: 0 } }; } });
  const cleanup = await h.mount();
  assert.match(h.html(), /Explain your actual contribution &lt;script&gt;/); assert.match(h.html(), /Faculty A/); assert.match(h.html(), /Unread/);
  h.button("Open report").props.onClick(); await flush();
  assert.deepEqual(calls, ["/student/notifications/12/read"]); assert.deepEqual(h.paths, ["/student/monthly-progress#report-7"]); cleanup();
});
test("mark all uses owner-scoped API and an empty inbox has a clear state", async t => {
  browser(t); const calls = [];
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", { async get() { return { data: { content: [], page: 0, totalPages: 0, unreadCount: 1 } }; }, async patch(url) { calls.push(url); return { data: { unreadCount: 0 } }; } });
  const cleanup = await h.mount(); assert.match(h.html(), /No notifications yet/);
  h.button("Mark all as read").props.onClick(); await flush(); assert.deepEqual(calls, ["/student/notifications/read-all"]); cleanup();
});
test("navigation badge displays the backend unread count", async t => {
  browser(t); const calls = [];
  const h = harness("components/NotificationMenu.tsx", "NotificationMenu", { async get(url) { calls.push(url); return { data: { unreadCount: 4 } }; } });
  const cleanup = await h.mount(); assert.deepEqual(calls, ["/student/notifications/unread-count"]);
  assert.match(h.html(), /4 unread notifications/); assert.ok(h.html().includes('href="/student/notifications"')); cleanup();
});
