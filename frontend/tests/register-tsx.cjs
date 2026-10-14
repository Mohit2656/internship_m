// Resolve real shared TS/TSX imports for the lightweight Node test harnesses.
// This lets legacy harnesses use new components without changing their fixtures.
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const ts = require("typescript");
const resolve = Module._resolveFilename;
Module._resolveFilename = function (request, parent, ...options) {
  if (request.startsWith("@/")) {
    const file = path.join(__dirname, "..", request.slice(2));
    request = [file, `${file}.ts`, `${file}.tsx`, path.join(file, "index.ts"), path.join(file, "index.tsx")]
      .find(candidate => fs.existsSync(candidate) && fs.statSync(candidate).isFile()) ?? file;
  }
  return resolve.call(this, request, parent, ...options);
};
for (const extension of [".ts", ".tsx"]) {
  Module._extensions[extension] = function (module, filename) {
    const compiled = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
      compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
    }).outputText;
    module._compile(compiled, filename);
  };
}
