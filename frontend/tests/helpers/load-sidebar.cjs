const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function loadSidebar(name) {
  const modules = new Map();
  function load(file) {
    if (modules.has(file)) return modules.get(file).exports;
    const module = { exports: {} };
    modules.set(file, module);
    const source = ts.transpileModule(fs.readFileSync(file, "utf8"), {
      compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 },
    }).outputText;
    new Function("require", "module", "exports", source)(key => {
      if (key === "next/link") return { default: ({ children, onNavigate, ...props }) => React.createElement("a", props, children) };
      if (key.startsWith("@/components/")) return load(path.join(__dirname, "../..", `${key.slice(2)}.tsx`));
      return require(key);
    }, module, module.exports);
    return module.exports;
  }
  return load(path.join(__dirname, "../../components", `${name}.tsx`))[name];
}

function sidebarRenderer(name) {
  const Sidebar = loadSidebar(name);
  return (pathname, hash = "") => renderToStaticMarkup(React.createElement(Sidebar, {
    pathname, hash, open: false, onNavigate() {}, onSignOut() {},
  }));
}

module.exports = { loadSidebar, sidebarRenderer };
