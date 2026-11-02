const { test } = require("node:test");
const assert = require("node:assert/strict");
const { sidebarRenderer } = require("./helpers/load-sidebar.cjs");

for (const [name, role] of [["StudentSidebar", "student"], ["MentorSidebar", "mentor"], ["AdminSidebar", "admin"]]) {
  test(`${role} Reports disclosure contains exactly the existing weekly and monthly destinations`, () => {
    const html = sidebarRenderer(name)(`/${role}/dashboard`);
    assert.equal((html.match(/>Reports</g) ?? []).length, 1);
    assert.match(html, new RegExp(`id="${role}-reports-toggle"[^>]*aria-expanded="false"[^>]*aria-controls="${role}-reports-options"`));
    const submenu = html.match(new RegExp(`<div[^>]*id="${role}-reports-options"[^>]*>(.*?)</div>`))[0];
    assert.match(submenu, /hidden=""/);
    assert.deepEqual([...submenu.matchAll(/href="([^"]+)"/g)].map(match => match[1]), [`/${role}/weekly-progress`, `/${role}/monthly-progress`]);
    assert.match(submenu, />Weekly Report</);
    assert.match(submenu, />Monthly Report</);
    assert.doesNotMatch(submenu, /attendance|\/reports"/);
  });

  test(`${role} report route opens its disclosure and selects only the current report`, () => {
    for (const report of ["weekly", "monthly"]) {
      const html = sidebarRenderer(name)(`/${role}/${report}-progress`);
      assert.match(html, new RegExp(`id="${role}-reports-toggle"[^>]*data-current="true"[^>]*aria-expanded="true"`));
      assert.equal((html.match(/aria-current="page"/g) ?? []).length, 1);
      assert.match(html, new RegExp(`href="/${role}/${report}-progress"[^>]*aria-current="page"`));
      assert.doesNotMatch(html, new RegExp(`id="${role}-reports-options"[^>]*hidden`));
    }
  });

  if (role !== "student") test(`${role} keeps its independent institutional report center`, () => {
    assert.match(sidebarRenderer(name)(`/${role}/reports`), new RegExp(`href="/${role}/reports"[^>]*aria-current="page"[^>]*>Report Center</a>`));
  });
}
