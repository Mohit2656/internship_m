const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path");
const { sidebarRenderer } = require("./helpers/load-sidebar.cjs");
const render = sidebarRenderer("AdminSidebar");

test("III Cell sidebar contains every destination",()=>{const html=render("/admin/dashboard");for(const label of ["Home","Student Directory","Second Internship Requests","Opportunities","Reports","Weekly Report","Monthly Report","Attendance","Attendance Report","Completions","Report Center","Student Accounts","Mentor Accounts","Academic Sessions","Notifications"])assert.ok(html.includes(`>${label}<`),label)});
test("III Cell nested and embedded destinations highlight",()=>{assert.match(render("/admin/opportunities/7/talent"),/href="\/admin\/opportunities"[^>]*aria-current="page"/);assert.match(render("/admin/dashboard","second-internship-requests"),/dashboard#second-internship-requests"[^>]*aria-current="page"/)});
