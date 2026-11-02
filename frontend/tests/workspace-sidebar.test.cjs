const { test } = require("node:test");
const assert = require("node:assert/strict");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
const { loadSidebar, sidebarRenderer } = require("./helpers/load-sidebar.cjs");

const workspaces = [
  { name: "StudentSidebar", prefix: "/student", label: "Student" },
  { name: "MentorSidebar", prefix: "/mentor", label: "Mentor" },
  { name: "AdminSidebar", prefix: "/admin", label: "III Cell" },
];

function nodes(tree) {
  if (!tree || typeof tree !== "object") return [];
  return [tree, ...React.Children.toArray(tree.props?.children).flatMap(nodes)];
}

test("every workspace keeps its own destinations and accessible navigation labels", () => {
  for (const { name, prefix, label } of workspaces) {
    const html = sidebarRenderer(name)(`${prefix}/dashboard`);
    const destinations = [...html.matchAll(/href="([^"]+)"/g)].map(match => match[1]);
    assert.ok(destinations.length > 0);
    assert.ok(destinations.every(href => href.startsWith(`${prefix}/`)), name);
    assert.equal(new Set(destinations).size, destinations.length, name);
    assert.match(html, new RegExp(`aria-label="${label} navigation"`));
    assert.match(html, new RegExp(`aria-label="${label} sections"`));
    assert.equal((html.match(/aria-current="page"/g) || []).length, 1, name);
  }
});

test("all workspace links and drawer close controls dismiss navigation without signing out", () => {
  for (const { name, prefix } of workspaces) {
    const navigations = [];
    let signOuts = 0;
    const Sidebar = loadSidebar(name);
    const shared = Sidebar({
      pathname: `${prefix}/dashboard`, open: true,
      onNavigate(href) { navigations.push(href); }, onSignOut() { signOuts++; },
    });
    const tree = shared.type(shared.props);
    const all = nodes(tree);
    const links = all.filter(node => node.props.className === "student-sidebar-link");
    for (const link of links) link.props.onNavigate();
    assert.deepEqual(navigations, links.map(link => link.props.href), name);
    const dismissals = all.filter(node => node.type === "button" && node.props["aria-label"] === "Close navigation");
    assert.equal(dismissals.length, 2, name);
    for (const button of dismissals) button.props.onClick();
    assert.deepEqual(navigations.slice(-2), [undefined, undefined], name);
    assert.equal(signOuts, 0, name);
    all.find(node => node.props.className === "student-sidebar-signout").props.onClick();
    assert.equal(signOuts, 1, name);
    assert.match(renderToStaticMarkup(tree), /class="student-sidebar" data-open="true"/);
  }
});

test("embedded sections do not select their home link and similarly named paths stay unselected", () => {
  for (const [name, pathname, hash] of [
    ["MentorSidebar", "/mentor/dashboard", "assigned-students"],
    ["MentorSidebar", "/mentor/attendance", "late-attendance-permissions"],
    ["AdminSidebar", "/admin/dashboard", "second-internship-requests"],
  ]) {
    const html = sidebarRenderer(name)(pathname, hash);
    assert.equal((html.match(/aria-current="page"/g) || []).length, 1, name);
    assert.match(html, new RegExp(`href="${pathname}#${hash}"[^>]*aria-current="page"`));
  }
  for (const { name, prefix } of workspaces) {
    assert.doesNotMatch(sidebarRenderer(name)(`${prefix}/notifications-archive`), /aria-current="page"/);
  }
});

test("Mentor and III Cell select Attendance and Attendance Report independently", () => {
  for (const [name, prefix] of [["MentorSidebar", "/mentor"], ["AdminSidebar", "/admin"]]) {
    for (const suffix of ["attendance", "attendance-report"]) {
      const html = sidebarRenderer(name)(`${prefix}/${suffix}`);
      assert.equal((html.match(/aria-current="page"/g) || []).length, 1, `${name}: ${suffix}`);
      assert.match(html, new RegExp(`href="${prefix}/${suffix}"[^>]*aria-current="page"`));
      assert.equal((html.match(new RegExp(`href="${prefix}/attendance-report"`, "g")) || []).length, 1);
    }
  }
});
