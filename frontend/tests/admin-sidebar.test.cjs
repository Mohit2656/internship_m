const{test}=require("node:test"),assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),ts=require("typescript"),React=require("react");const{renderToStaticMarkup}=require("react-dom/server");
function render(pathname,hash="",activeSection=""){const source=fs.readFileSync(path.join(__dirname,"../components/AdminSidebar.tsx"),"utf8"),module={exports:{}};new Function("require","module","exports",ts.transpileModule(source,{compilerOptions:{jsx:ts.JsxEmit.ReactJSX,module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText)(key=>key==="next/link"?{default:({children,...props})=>React.createElement("a",props,children)}:require(key),module,module.exports);return renderToStaticMarkup(React.createElement(module.exports.AdminSidebar,{pathname,hash,activeSection,open:false,onNavigate(){},onSignOut(){}}))}
test("III Cell sidebar contains every destination",()=>{const html=render("/admin/dashboard");for(const label of ["Home","Academic Sessions","Student Directory","Recent Activity","Second Internship Requests","Opportunities","Progress","Attendance","Completions","Reports","Accounts","Notifications"])assert.ok(html.includes(`>${label}<`),label)});
test("III Cell nested and embedded destinations highlight",()=>{
  assert.match(render("/admin/opportunities/7/talent"),/href="\/admin\/opportunities"[^>]*aria-current="page"/);
  assert.match(render("/admin/second-internship-requests"),/href="\/admin\/second-internship-requests"[^>]*aria-current="page"/);
  assert.match(render("/admin/progress"),/href="\/admin\/progress"[^>]*aria-current="page"/);
  assert.match(render("/admin/weekly-progress"),/href="\/admin\/progress"[^>]*aria-current="page"/);
  assert.match(render("/admin/accounts"),/href="\/admin\/accounts"[^>]*aria-current="page"/);
  assert.match(render("/admin/student-accounts"),/href="\/admin\/accounts"[^>]*aria-current="page"/);
});
test("III Cell student profile preserves attendance context or student directory context",()=>{
  assert.match(render("/admin/students/4","/admin/attendance","/admin/attendance"),/href="\/admin\/attendance"[^>]*aria-current="page"/);
  assert.doesNotMatch(render("/admin/students/4","/admin/attendance","/admin/attendance"),/href="\/admin\/students"[^>]*aria-current="page"/);
  assert.match(render("/admin/students/4"),/href="\/admin\/students"[^>]*aria-current="page"/);
  assert.doesNotMatch(render("/admin/students/4"),/href="\/admin\/attendance"[^>]*aria-current="page"/);
});

