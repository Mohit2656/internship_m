const {test}=require("node:test"),assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const source=fs.readFileSync(path.join(__dirname,"../app/admin/students/page.tsx"),"utf8");
test("III Cell directory uses a compact six-column table with local overflow",()=>{for(const heading of ["Student","Academic","Internship","Attendance","Progress","Actions"])assert.ok(source.includes(`\"${heading}\"`));assert.match(source,/min-w-\[980px\]/);assert.doesNotMatch(source,/min-w-\[1500px\]/)});
test("directory preserves filters pagination and student actions",()=>{for(const value of ["Apply Filters","Previous","Next","View Attendance","ResetPasswordButton","DeleteStudentButton","Recent updates"])assert.ok(source.includes(value),value)});
