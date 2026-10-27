const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");
const React = require("react"), { renderToStaticMarkup } = require("react-dom/server");
const flush = () => new Promise(resolve => setImmediate(resolve));
function harness(file, name, api, { props = {}, imports: overrides = {} } = {}) {
  const states = [], effects = [], paths = []; let cursor = 0;
  const hooks = { useState(initial) { const i = cursor++; if (!(i in states)) states[i] = initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; }, useRef(initial) { return { current: initial }; }, useEffect(effect) { if (!effects.length) effects.push(effect); } };
  const m = { exports: {} };
  const imports = { react: hooks, "@/lib/api": { api }, "next/navigation": { useRouter: () => ({ push: url => paths.push(url) }), usePathname: () => props.role === "MENTOR" ? "/mentor/notifications" : "/student/notifications" }, "next/link": { default: ({ children, ...props }) => React.createElement("a", props, children) }, "@/components/AppFrame": { AppFrame: () => null }, "@/components/ProtectedRoute": { ProtectedRoute: () => null }, "@/lib/monthly-progress": { formatDateTime: value => value }, "@/lib/notifications": { NOTIFICATIONS_CHANGED: "changed", syncNotificationCount() {} }, ...overrides };
  new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText)(key => imports[key] || require(key), m, m.exports);
  function tree() { cursor = 0; return m.exports[name](props); }
  function find(node, label) { if (!node || typeof node !== "object") return; if (node.type === "button" && node.props.children === label) return node; for (const child of React.Children.toArray(node.props?.children)) { const result = find(child, label); if (result) return result; } }
  return { tree, html: () => renderToStaticMarkup(tree()), paths, button: label => find(tree(), label), async mount() { tree(); const cleanup = effects[0](); await flush(); return cleanup; } };
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

test("published opportunity shows publication details and opens its exact detail route", async t => {
  browser(t); const calls = [];
  const opportunity = { ...item, id: 13, type: "OPPORTUNITY_PUBLISHED", title: "Software internship", message: "Example Company\nBuild accessible applications.", rejectionReason: null, mentorName: null, actionUrl: "/student/opportunities/42" };
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", { async get() { return { data: { content: [opportunity], totalPages: 1, unreadCount: 1 } }; }, async patch(url) { calls.push(url); return { data: { unreadCount: 0 } }; } });
  const cleanup = await h.mount();
  assert.match(h.html(), /New opportunity/); assert.match(h.html(), /Example Company/); assert.match(h.html(), /Build accessible applications/); assert.match(h.html(), /Published: /);
  h.button("View opportunity").props.onClick(); await flush();
  assert.deepEqual(calls, ["/student/notifications/13/read"]); assert.deepEqual(h.paths, ["/student/opportunities/42"]); cleanup();
});

test("opening a read opportunity keeps its read timestamp and skips another update", async t => {
  browser(t); const calls = [];
  const opportunity = { ...item, type: "OPPORTUNITY_PUBLISHED", readAt: "2026-10-22T08:00:00Z", actionUrl: "/student/opportunities/42" };
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", { async get() { return { data: { content: [opportunity], totalPages: 1, unreadCount: 0 } }; }, async patch(url) { calls.push(url); } });
  const cleanup = await h.mount(); h.button("View opportunity").props.onClick(); await flush();
  assert.deepEqual(calls, []); assert.deepEqual(h.paths, ["/student/opportunities/42"]); cleanup();
});

test("badge refreshes after read actions and removes the count at zero", async t => {
  browser(t); const events = new Map(); let count = 2;
  global.window.addEventListener = (name, callback) => events.set(name, callback);
  global.window.removeEventListener = name => events.delete(name);
  const h = harness("components/NotificationMenu.tsx", "NotificationMenu", { async get() { return { data: { unreadCount: count } }; } });
  const cleanup = await h.mount(); assert.match(h.html(), /notification-badge/);
  count = 0; events.get("changed")(); await flush();
  assert.doesNotMatch(h.html(), /notification-badge/); assert.match(h.html(), /0 unread notifications/);
  cleanup(); assert.equal(events.size, 0);
});

const attendance = { ...item, id: 31, type: "ATTENDANCE_SUBMITTED", title: "Attendance submitted", message: "Student A submitted attendance for 2026-10-27.", rejectionReason: null, mentorName: null, actionUrl: "/mentor/attendance" };

test("mentor attendance notification uses the mentor inbox and opens attendance after marking read", async t => {
  browser(t); const requests = [], updates = [];
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", {
    async get(url, options) { requests.push([url, options.params]); return { data: { content: [attendance], page: 0, totalPages: 1, unreadCount: 1 } }; },
    async patch(url) { updates.push(url); return { data: { unreadCount: 0 } }; },
  }, { props: { role: "MENTOR" } });
  const cleanup = await h.mount();
  assert.deepEqual(requests, [["/mentor/notifications", { page: 0 }]]);
  assert.match(h.html(), /Student A submitted attendance for 2026-10-27/); assert.match(h.html(), /Submitted: /);
  assert.match(h.html(), /href="\/mentor\/dashboard"/); assert.doesNotMatch(h.html(), /Browse opportunities|Deadline reminder/);
  h.button("View attendance").props.onClick(); await flush();
  assert.deepEqual(updates, ["/mentor/notifications/31/read"]); assert.deepEqual(h.paths, ["/mentor/attendance"]); cleanup();
});

test("mentor can mark an individual submission and all submissions read", async t => {
  browser(t); const updates = [];
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", {
    async get() { return { data: { content: [attendance], totalPages: 1, unreadCount: 1 } }; },
    async patch(url) { updates.push(url); return { data: { unreadCount: 0 } }; },
  }, { props: { role: "MENTOR" } });
  const cleanup = await h.mount();
  h.button("Mark as read").props.onClick(); await flush();
  h.button("Mark all as read").props.onClick(); await flush();
  assert.deepEqual(updates, ["/mentor/notifications/31/read", "/mentor/notifications/read-all"]);
  assert.deepEqual(h.paths, []); cleanup();
});

test("empty mentor inbox explains attendance submissions and links to attendance", async t => {
  browser(t);
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", { async get() { return { data: { content: [], totalPages: 0, unreadCount: 0 } }; } }, { props: { role: "MENTOR" } });
  const cleanup = await h.mount();
  assert.match(h.html(), /No notifications yet/); assert.match(h.html(), /Attendance submissions from your assigned students will appear here/);
  assert.match(h.html(), /href="\/mentor\/attendance"/); assert.doesNotMatch(h.html(), /Browse opportunities/);
  assert.equal(h.button("Mark all as read").props.disabled, true); cleanup();
});

test("mentor badge polls and refreshes on focus and read changes, then cleans up", async t => {
  browser(t); const events = new Map(), calls = []; let poll, count = 1, cleared;
  global.window.setInterval = (callback, delay) => { poll = callback; assert.equal(delay, 30_000); return 4; };
  global.window.clearInterval = id => { cleared = id; };
  global.window.addEventListener = (name, callback) => events.set(name, callback);
  global.window.removeEventListener = name => events.delete(name);
  const h = harness("components/NotificationMenu.tsx", "NotificationMenu", { async get(url) { calls.push(url); return { data: { unreadCount: count } }; } }, { props: { role: "MENTOR" } });
  const cleanup = await h.mount();
  assert.match(h.html(), /href="\/mentor\/notifications"/); assert.match(h.html(), /aria-current="page"/);
  count = 2; poll(); await flush(); assert.match(h.html(), /2 unread notifications/);
  count = 3; events.get("focus")(); await flush(); assert.match(h.html(), /3 unread notifications/);
  count = 0; events.get("changed")(); await flush(); assert.doesNotMatch(h.html(), /notification-badge/);
  assert.deepEqual(calls, Array(4).fill("/mentor/notifications/unread-count"));
  cleanup(); assert.equal(cleared, 4); assert.equal(events.size, 0);
});

test("mentor inbox does not navigate to cross-role or external action URLs", async t => {
  browser(t);
  for (const actionUrl of ["/student/monthly-progress", "https://example.com/mentor/attendance", "//example.com/mentor/attendance"]) {
    const h = harness("components/NotificationsContent.tsx", "NotificationsContent", { async get() { return { data: { content: [{ ...attendance, actionUrl }], totalPages: 1, unreadCount: 1 } }; }, async patch() {} }, { props: { role: "MENTOR" } });
    const cleanup = await h.mount(); h.button("View attendance").props.onClick(); await flush(); assert.deepEqual(h.paths, []); cleanup();
  }
});

test("failed mentor read update displays an error and keeps the mentor on the inbox", async t => {
  browser(t);
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", { async get() { return { data: { content: [attendance], totalPages: 1, unreadCount: 1 } }; }, async patch() { throw new Error("unavailable"); } }, { props: { role: "MENTOR" } });
  const cleanup = await h.mount(); h.button("View attendance").props.onClick(); await flush();
  assert.match(h.html(), /role="alert"/); assert.match(h.html(), /Notifications could not be updated/); assert.deepEqual(h.paths, []); cleanup();
});

test("mentor notification route enforces mentor access and passes mentor scope to shared inbox", () => {
  const h = harness("app/mentor/notifications/page.tsx", "default", {}, { imports: { "@/components/NotificationsContent": { NotificationsContent: () => null } } });
  const page = h.tree();
  assert.equal(page.props.role, "MENTOR"); assert.equal(page.props.children.props.children.props.role, "MENTOR");
});

test("app navigation enables notification bell for mentors and students only", () => {
  for (const role of ["STUDENT", "MENTOR", "ADMIN"]) {
    const h = harness("components/AppFrame.tsx", "AppFrame", {}, { imports: {
      "@/lib/auth": { useAuth: () => ({ user: { id: 5, role, roleLabel: role }, async logout() {} }) },
      "@/components/ThemeToggle": { ThemeToggle: () => null },
      "@/components/NotificationMenu": { NotificationMenu: ({ role: notificationRole }) => React.createElement("span", { "data-notification-role": notificationRole }) },
    } });
    if (role === "ADMIN") assert.doesNotMatch(h.html(), /data-notification-role/);
    else assert.ok(h.html().includes(`data-notification-role="${role}"`));
  }
});

function nodes(tree) {
  if (!tree || typeof tree !== "object") return [];
  return [tree, ...[tree.props?.children].flat(Infinity).flatMap(nodes)];
}
function cardButton(card, label) {
  const result = nodes(card).find(node => node.type === "button" && node.props.children === label);
  assert.ok(result, `Notification card should have a '${label}' button`);
  return result;
}
const mentorApproval = {
  ...item, id: 71, type: "INTERNSHIP_MENTOR_APPROVED", title: "Internship approved by faculty mentor",
  message: "Your faculty mentor approved your registered internship workplace. IIIC approval is still required.",
  rejectionReason: null, mentorName: "Faculty A", actionUrl: "/student/internship/details",
  createdAt: "2026-10-27T08:00:00Z", readAt: null,
};
const iiicApproval = {
  ...mentorApproval, id: 72, type: "INTERNSHIP_IIIC_APPROVED", title: "Internship approved by IIIC coordinator",
  message: "Your IIIC coordinator approved your registered internship workplace. Both approvals are now recorded.",
  mentorName: null, createdAt: "2026-10-27T09:00:00Z",
};

test("student inbox displays separate mentor and IIIC approval cards with their own approval timestamps", async t => {
  browser(t);
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", {
    async get() { return { data: { content: [mentorApproval, iiicApproval], page: 0, totalPages: 1, unreadCount: 2 } }; },
  });
  const cleanup = await h.mount();
  const cards = nodes(h.tree()).filter(node => node.type === "article");
  assert.equal(cards.length, 2);
  for (const [index, category, notice] of [[0, "Faculty mentor approval", mentorApproval], [1, "IIIC coordinator approval", iiicApproval]]) {
    const html = renderToStaticMarkup(cards[index]);
    assert.ok(html.includes(category));
    assert.ok(html.includes(notice.title));
    assert.ok(html.includes(notice.message));
    assert.match(html, /Approved: /);
    assert.doesNotMatch(html, /Deadline reminder|Notified:|Open task|Rejection reason/);
    const timestamp = nodes(cards[index]).find(node => node.type === "time");
    assert.equal(timestamp.props.dateTime, notice.createdAt);
    assert.equal(timestamp.props.children, notice.createdAt);
    assert.equal(cardButton(cards[index], "View internship").props.disabled, false);
    assert.match(html, />Unread</);
  }
  assert.match(renderToStaticMarkup(cards[0]), /Faculty mentor: Faculty A/);
  assert.doesNotMatch(renderToStaticMarkup(cards[1]), /Faculty mentor:/);
  assert.match(h.html(), />2 unread</);
  cleanup();
});

test("opening each student approval marks only its own notification read and preserves the other unread approval", async t => {
  browser(t);
  let poll;
  global.window.setInterval = callback => { poll = callback; return 1; };
  const content = [{ ...mentorApproval }, { ...iiicApproval }], updates = [], requests = [];
  const h = harness("components/NotificationsContent.tsx", "NotificationsContent", {
    async get(url) {
      requests.push(url);
      return { data: { content: content.map(notice => ({ ...notice })), page: 0, totalPages: 1,
        unreadCount: content.filter(notice => !notice.readAt).length } };
    },
    async patch(url) {
      updates.push(url);
      const target = content.find(notice => url === `/student/notifications/${notice.id}/read`);
      assert.ok(target, "Read update must belong to one selected approval notification");
      target.readAt = "2026-10-27T10:00:00Z";
      return { data: { unreadCount: content.filter(notice => !notice.readAt).length } };
    },
  });
  const cleanup = await h.mount();
  let cards = nodes(h.tree()).filter(node => node.type === "article");
  cardButton(cards[1], "View internship").props.onClick(); await flush();
  assert.deepEqual(updates, ["/student/notifications/72/read"]);
  assert.deepEqual(h.paths, ["/student/internship/details"]);
  poll(); await flush();
  cards = nodes(h.tree()).filter(node => node.type === "article");
  assert.match(renderToStaticMarkup(cards[0]), />Unread</);
  assert.match(renderToStaticMarkup(cards[1]), />Read</);
  assert.doesNotMatch(renderToStaticMarkup(cards[1]), /Mark as read/);
  assert.match(h.html(), />1 unread</);

  cardButton(cards[0], "View internship").props.onClick(); await flush();
  assert.deepEqual(updates, ["/student/notifications/72/read", "/student/notifications/71/read"]);
  assert.deepEqual(h.paths, ["/student/internship/details", "/student/internship/details"]);
  poll(); await flush();
  cards = nodes(h.tree()).filter(node => node.type === "article");
  for (const card of cards) assert.match(renderToStaticMarkup(card), />Read</);
  assert.match(h.html(), />0 unread</);
  assert.equal(h.button("Mark all as read").props.disabled, true);

  cardButton(cards[1], "View internship").props.onClick(); await flush();
  assert.equal(updates.length, 2, "Opening an already read approval must not repeat the read update");
  assert.equal(h.paths.at(-1), "/student/internship/details");
  assert.deepEqual(requests, Array(3).fill("/student/notifications"));
  cleanup();
});
