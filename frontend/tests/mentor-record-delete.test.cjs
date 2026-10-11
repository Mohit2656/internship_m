const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"); const path = require("node:path"); const ts = require("typescript");
const React = require("react"); const { renderToStaticMarkup } = require("react-dom/server");
function harness(kind, remove, refresh) {
 const slots=[]; let cursor=0;
 const hooks={useState(initial){const i=cursor++;if(!(i in slots))slots[i]=initial;return [slots[i],v=>slots[i]=v];},useRef(initial){const i=cursor++;if(!(i in slots))slots[i]={current:initial};return slots[i];}};
 const compiled=ts.transpileModule(fs.readFileSync(path.join(__dirname,"../components/MentorRecordDelete.tsx"),"utf8"),{compilerOptions:{jsx:ts.JsxEmit.ReactJSX,module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
 const module={exports:{}};new Function("require","module","exports",compiled)(name=>name==="react"?hooks:name==="@/lib/api"?{api:{delete:remove}}:require(name),module,module.exports);
 function render(){cursor=0;return module.exports.MentorRecordDelete({kind,id:42,description:"Student Example (UID-42) - 10 November 2026",onDeleted:refresh});}
 function nodes(type){const result=[];function walk(n){if(!n||typeof n!=="object")return;if(n.type===type)result.push(n);React.Children.forEach(n.props?.children,walk);}walk(render());return result;}
 return {button:label=>nodes("button").find(n=>n.props.children===label),reason:value=>nodes("textarea")[0].props.onChange({target:{value}}),html:()=>renderToStaticMarkup(render())};
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
for(const kind of ["attendance","monthly-progress"]) test(`${kind}: confirmation, reason, authenticated route, pending guard, refresh and success`,async()=>{
 let calls=0,refreshed=0,complete;
 const h=harness(kind,(url,config)=>{calls++;assert.equal(url,`/mentor/${kind}/42`);assert.equal(config.data.reason,"Wrong record submitted");return new Promise(r=>complete=r);},()=>{refreshed++;});
 h.button("Delete").props.onClick();assert.equal(calls,0);assert.match(h.html(),/Student Example/);assert.match(h.html(),/10 November 2026/);assert.equal(h.button("Delete selected record").props.disabled,true);
 h.reason("  Wrong record submitted  ");const confirm=h.button("Delete selected record");confirm.props.onClick();confirm.props.onClick();assert.equal(calls,1);assert.equal(h.button("Cancel").props.disabled,true);
 complete();await flush();assert.equal(refreshed,1);assert.match(h.html(),/Selected record deleted/);assert.equal(h.button("Delete selected record"),undefined);
});
test("cancel sends no request and clears reason",()=>{
 let calls=0;const h=harness("attendance",()=>calls++,()=>{});h.button("Delete").props.onClick();h.reason("Wrong record");h.button("Cancel").props.onClick();assert.equal(calls,0);h.button("Delete").props.onClick();assert.equal(h.button("Delete selected record").props.disabled,true);
});
test("backend failure keeps confirmation, shows safe error and never refreshes",async()=>{
 let refresh=0;const h=harness("attendance",async()=>{throw Error("sensitive detail");},()=>refresh++);h.button("Delete").props.onClick();h.reason("Wrong record");h.button("Delete selected record").props.onClick();await flush();assert.equal(refresh,0);assert.match(h.html(),/Unable to delete or refresh/);assert.doesNotMatch(h.html(),/sensitive detail/);assert.equal(h.button("Delete selected record").props.disabled,false);
});
test("refresh failure after committed deletion reports ambiguity without reopening confirmation",async()=>{
 const h=harness("monthly-progress",async()=>{},async()=>{throw Error("refresh error");});h.button("Delete").props.onClick();h.reason("Wrong report");h.button("Delete selected record").props.onClick();await flush();assert.match(h.html(),/Refresh the list before retrying/);assert.equal(h.button("Delete selected record"),undefined);
});
