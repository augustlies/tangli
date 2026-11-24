// Run with node and a JSON file containing GradeGridQuery.triggerJs()/readJs().
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const scripts = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
let reloads = 0;
const events = {};
const table = {
  grid: {hDiv: {loading: false}},
  p: {rowNum: 15, page: 1, lastpage: 4, records: 57, postData: {xnm: '', xqm: ''}},
  querySelectorAll() { return this.rows; },
  rows: Array.from({length: 15}, () => ({cells: [{innerText: 'old'}]})),
};
const grid = {
  0: table, length: 1,
  jqGrid(action, params) { Object.assign(table.p, params); return this; },
  off() { for (const key of Object.keys(events)) delete events[key]; return this; },
  on(event, fn) { events[event.split('.')[0]] = fn; return this; },
  trigger(event) { if (event === 'reloadGrid') { reloads++; begin(); } },
};
function begin() { table.grid.hDiv.loading = true; events.jqGridBeforeRequest(); }
function complete(count) {
  table.rows = Array.from({length: count}, () => ({cells: [{innerText: 'new'}]}));
  table.p.lastpage = 1;
  table.grid.hDiv.loading = false;
  events.jqGridLoadComplete();
}
const controls = {xnm: {value: '2026'}, xqm: {value: '3'}, search_go: {click: begin}};
const tasks = [];
const context = vm.createContext({
  window: {jQuery: () => grid},
  document: {getElementById: id => controls[id]},
  setTimeout: fn => tasks.push(fn),
});
const trigger = () => vm.runInContext(scripts.trigger, context);
const read = () => vm.runInContext(scripts.read, context);
assert.equal(trigger(), 'CLICKED');
assert.equal(table.p.rowNum, 500);
assert.equal(controls.xnm.value, '');
assert.equal(controls.xqm.value, '');
assert.equal(read(), ''); // old 15 rows cannot be saved while request runs
complete(57);
assert.equal(read(), ''); // search completion alone is not the verified reload
tasks.shift()();
assert.equal(reloads, 1);
assert.equal(read(), ''); // reload still pending
complete(15);
assert.equal(read(), ''); // partial DOM even after completion
complete(57);
assert.equal(JSON.parse(read()).rows.length, 57);
table.p.records = 501;
assert.equal(read(), ''); // pagination cap never silently truncates local history
table.p.records = 57;
table.p.lastpage = 2;
assert.equal(read(), '');
table.p.lastpage = 1;
table.p.postData.xnm = '2026';
assert.equal(read(), ''); // a subsequent filtered query cannot replace all grades
table.p.postData.xnm = '';
begin();
assert.equal(read(), ''); // subsequent query invalidates completed snapshot
events.jqGridLoadError();
complete(57);
assert.equal(read(), ''); // error cannot establish completeness
table.grid.hDiv.loading = true;
assert.equal(trigger(), 'WAIT'); // don't attach to an old in-flight query
console.log('grade-grid-query: 16 assertions passed');
