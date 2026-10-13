// Tiny DOM adapter exercises the generated offline page without a browser/network.
const fs = require('fs'), vm = require('vm'), assert = require('assert');
class Element {
    constructor() { this.children = []; this.dataset = {}; this.style = {}; this.value = ''; }
    append(...children) { this.children.push(...children); }
    setAttribute(key, value) { this[key] = value; }
    focus() {} select() { this.selected = true; } click() {} remove() {}
}
const source = fs.readFileSync(process.argv[2], 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const count = Number(process.argv[3]), saved = new Map();
function boot() {
    const elements = {};
    const context = {console, navigator: {}, window: {confirm: () => true}, setTimeout: () => {},
        Blob: class {}, URL: {createObjectURL: () => 'blob:test', revokeObjectURL: () => {}},
        localStorage: {getItem: key => saved.get(key), setItem: (key, value) => saved.set(key, value)},
        document: {body: new Element(), createElement: () => new Element(), getElementById: id => elements[id] ||= new Element()}};
    vm.createContext(context); vm.runInContext(source, context);
    return {context, elements, query: expression => vm.runInContext(expression, context)};
}
(async () => {
    let page = boot();
    assert.equal(page.query('cards.size'), count);
    if (count === 60) {
        assert(page.query('cards.get(rows[0].file).toggle.children[0].style.width === rows[0].preview_style.width'));
        assert(page.query('cards.get(rows[0].file).toggle.children[0].style.transform === rows[0].preview_style.transform'));
    }
    assert.equal(page.query('Object.keys(reviews).length'), 0);
    assert.equal(page.query('payload().displayedFiles.length'), count);
    assert.equal(page.query('payload().defaultStatus'), 'ok');
    if (page.query('rows.some(row => row.changed_since_accepted)')) {
        assert(page.query("cards.get(rows.find(row => row.changed_since_accepted).file).card.children.some(e => e.className === 'caption change-marker' && e.children[0].href === 'connection-regression/index.html')"));
        assert(page.query('!payload().reviews[rows.find(row => row.changed_since_accepted).file]'));
    }
    if (page.query('!!rows[0].secondary_file')) {
        page.query("cards.get(rows[0].file).card.children.find(e => e.className === 'card-footer').children.find(e => e.className === 'view-switch').onclick()");
        assert(page.query('cards.get(rows[0].file).toggle.children[0].src === rows[0].secondary_file'));
        assert.equal(page.query('Object.keys(reviews).length'), 0);
    }
    assert(page.query('reportText()').includes(count === 60 ? 'All 60 displayed cases.' : 'Hidden cases are not reviewed.'));
    page.query('cards.get(rows[0].file).toggle.onclick()');
    page.query("cards.get(rows[0].file).notes.value = 'junction seam'; cards.get(rows[0].file).notes.oninput()");
    assert(page.query('reportText()').includes('junction seam'));
    assert(page.query('Object.keys(payload().reviews)[0] === rows[0].file'));
    if (page.query('!!rows[0].secondary_file')) {
        page.query("cards.get(rows[0].file).card.children.find(e => e.className === 'card-footer').children.find(e => e.className === 'view-switch').onclick()");
        assert(page.query('cards.get(rows[0].file).toggle.children[0].src === rows[0].file'));
        assert.equal(page.query('Object.keys(reviews).length'), 1);
    }
    page = boot();
    assert.equal(page.query('Object.keys(reviews).length'), 1);
    assert(page.query('payload().displayedFiles.includes(Object.keys(reviews)[0])'));
    await page.elements['copy-report'].onclick();
    assert(page.elements['feedback-report'].selected);
    page.elements['export-json'].onclick();
    page.query('cards.get(rows[0].file).toggle.onclick()');
    assert.equal(page.query('Object.keys(reviews).length'), 0);
    page.query('cards.get(rows[1].file).toggle.onclick()');
    page.elements['reset-reviews'].onclick();
    assert.equal(page.query('Object.keys(reviews).length'), 0);
})().catch(error => { console.error(error); process.exitCode = 1; });
