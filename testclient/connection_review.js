// Embedded into index.html by review_connections.py; no network or runtime imports.
'use strict';
const $ = id => document.getElementById(id);
const storageKey = 'vandorlabs.connection-review.v1:' + encodeURIComponent(captureId);
const rowByFile = new Map(rows.map(row => [row.file, row]));
const searchIndex = new Map(rows.map(row => [row.file, (row.name + ' ' + row.title + ' ' + row.cells.join(' ')).toLowerCase()]));
let reviews = Object.create(null), page = 0, storageAvailable = true;
const EMPTY_REVIEW = Object.freeze({status: 'unreviewed', cells: Object.freeze([]), notes: ''});

function el(tag, text) {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    return element;
}
function button(text, action) {
    const result = el('button', text);
    result.type = 'button'; result.onclick = action;
    return result;
}
function flagged(review) { return review && ['wrong', 'unsure', 'skip'].includes(review.status); }
function reviewFor(row) { return reviews[row.file] || EMPTY_REVIEW; }
function cleanReview(file, source) {
    const row = rowByFile.get(file);
    if (!row || !source || typeof source !== 'object' || !['wrong', 'unsure', 'ok', 'skip', 'unreviewed'].includes(source.status)) return null;
    return {
        status: source.status,
        cells: [...new Set((Array.isArray(source.cells) ? source.cells : []).filter(cell => Number.isInteger(cell) && cell >= 1 && cell <= row.cells.length))].sort((a, b) => a - b),
        notes: typeof source.notes === 'string' ? source.notes.slice(0, 20000) : '',
        updatedAt: typeof source.updatedAt === 'string' ? source.updatedAt : ''
    };
}
function readReviews(value) {
    const result = Object.create(null);
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('The reviews field must be an object keyed by screenshot filename.');
    for (const [file, review] of Object.entries(value)) {
        const cleaned = cleanReview(file, review);
        if (cleaned) result[file] = cleaned;
    }
    return result;
}
function storageMessage() {
    $('storage-status').textContent = storageAvailable
        ? 'Autosave is on for this capture in this browser. Export JSON for a portable backup.'
        : 'Browser storage is unavailable. Reviews remain in this page until it closes; export JSON to keep them.';
}
function payload(includeAll = true) {
    const selected = includeAll ? reviews : Object.fromEntries(Object.entries(reviews).filter(([, review]) => flagged(review)));
    return {schema: 'vandorlabs-connection-review-v1', captureId, exportedAt: new Date().toISOString(), reviews: selected};
}
function save() {
    try { localStorage.setItem(storageKey, JSON.stringify(payload())); storageAvailable = true; }
    catch (_) { storageAvailable = false; }
    storageMessage();
}
try {
    const saved = localStorage.getItem(storageKey);
    if (saved) {
        const value = JSON.parse(saved);
        if (value.schema !== 'vandorlabs-connection-review-v1' || value.captureId !== captureId) throw new Error('Saved review format differs.');
        reviews = readReviews(value.reviews);
    }
} catch (_) { storageAvailable = false; }

function reportText() {
    const selected = rows.filter(row => flagged(reviews[row.file]));
    const counts = reviewCounts();
    const lines = ['Wall connection feedback', 'Capture: ' + captureId,
        `Wrong: ${counts.wrong}; unsure: ${counts.unsure}; skip / not needed: ${counts.skip}; OK: ${counts.ok}; unreviewed: ${counts.unreviewed}`,
        'Case IDs are screenshot filenames. Roles use world positions C center, N north, S south, W west, E east. Each scene lists its exact roles; plans use north up.', ''];
    if (!selected.length) lines.push('No Wrong, Unsure or Skip screenshots flagged.');
    for (const row of selected) {
        const review = reviews[row.file];
        lines.push(`${review.status.toUpperCase()} | ${row.file}`, row.title,
            'Roles: ' + (review.cells.length ? review.cells.map(cell => row.roles[cell - 1].id).join(', ') : 'whole junction / unspecified'),
            'Placement order: ' + row.placement_order.join(' → '));
        for (const cell of review.cells) lines.push('  ' + row.cells[cell - 1]);
        if (review.notes.trim()) lines.push('Notes: ' + review.notes.trim());
        lines.push('');
    }
    return lines.join('\n');
}
function reviewCounts() {
    const counts = {wrong: 0, unsure: 0, ok: 0, skip: 0, unreviewed: 0};
    for (const row of rows) counts[reviewFor(row).status]++;
    return counts;
}
function filtered() {
    const query = $('search').value.toLowerCase(), status = $('review-filter').value,
        topology = $('kind').value, family = $('family').value, facing = $('direction').value,
        placement = $('upper-lower').value, material = $('material').value, missingOnly = $('missing').checked;
    if (!query && !status && !topology && !family && !facing && !placement && !material && !missingOnly) return rows;
    return rows.filter(row =>
        (!topology || row.topology === topology) &&
        (!family || row.family === family) &&
        (!facing || row.facing === facing) &&
        (!placement || row.upper_lower === placement) &&
        matchesMaterial(row, material) &&
        (!missingOnly || !row.exists) &&
        (!status || (status === 'flagged' ? flagged(reviewFor(row)) : reviewFor(row).status === status)) &&
        (!query || searchIndex.get(row.file).includes(query)));
}
function matchesMaterial(row, selected) {
    if (!selected) return true;
    if (selected === 'mixed') return new Set(row.materials).size > 1;
    if (selected.startsWith('all-')) return row.materials.every(material => material === selected.slice(4));
    return row.materials.includes(selected);
}
function focusRow(row) {
    const index = filtered().findIndex(item => item.file === row.file);
    if (index < 0) {
        for (const id of ['kind', 'family', 'direction', 'upper-lower', 'material', 'review-filter', 'search']) $(id).value = '';
        $('missing').checked = false;
    }
    $('page-size').value = '1';
    page = filtered().findIndex(item => item.file === row.file);
    render(); $('cards').scrollIntoView({block: 'start'});
}
function updateFeedback() {
    const counts = reviewCounts();
    $('feedback-summary').textContent = `Flagged feedback: ${counts.wrong} wrong · ${counts.unsure} unsure · ${counts.skip} skip · ${counts.ok} OK · ${counts.unreviewed} unreviewed`;
    $('feedback-report').value = reportText();
    const list = $('feedback-list'); list.replaceChildren();
    const filter = $('feedback-filter').value;
    const selected = rows.filter(row => flagged(reviews[row.file]) && (filter === 'flagged' || reviews[row.file].status === filter));
    if (!selected.length) list.append(el('p', 'No screenshots match this feedback filter.'));
    for (const row of selected) {
        const review = reviews[row.file], item = el('div'); item.className = 'feedback-item';
        item.append(button(`${review.status.toUpperCase()} · ${row.file}`, () => focusRow(row)));
        item.append(el('p', 'Roles: ' + (review.cells.length ? review.cells.map(cell => row.roles[cell - 1].id).join(', ') : 'whole junction / unspecified')));
        if (review.notes) item.append(el('p', review.notes));
        list.append(item);
    }
}
function editReview(row, changes) {
    reviews[row.file] = {...reviewFor(row), ...changes, updatedAt: new Date().toISOString()};
    save(); updateFeedback();
}
function reviewEditor(row, card) {
    const editor = el('section'); editor.className = 'review-tools';
    const planHeading = el('div', 'Top-down plan · N ↑'); planHeading.className = 'hint';
    const plan = el('div'); plan.className = 'junction-plan'; plan.setAttribute('aria-label', 'Block positions, north up');
    for (let z = -1; z <= 1; z++) for (let x = -1; x <= 1; x++) {
        const role = row.roles.find(item => item.position[0] === x && item.position[1] === z);
        const arrows = {north: '↑', east: '→', south: '↓', west: '←'};
        const cell = el('div', role ? role.id + arrows[role.facing] : '·'); cell.className = 'plan-cell' + (role ? '' : ' empty');
        if (role) { cell.title = `${role.id}: ${role.location}, facing ${role.facing}, ${role.material}`; cell.setAttribute('aria-label', cell.title); }
        plan.append(cell);
    }
    editor.append(planHeading, plan, el('p', 'Place: ' + row.placement_order.join(' → ')));
    const statuses = el('div'); statuses.className = 'status-buttons';
    function refreshStatus() {
        const status = reviewFor(row).status;
        card.dataset.review = status;
        for (const child of statuses.children) child.setAttribute('aria-pressed', String(child.dataset.status === status));
    }
    for (const [status, label] of [['wrong', 'Wrong'], ['unsure', 'Unsure'], ['ok', 'OK'], ['skip', 'Skip / not needed']]) {
        const choice = button(label, () => {
            editReview(row, {status}); refreshStatus();
            // Keep typing focus and the current screenshot visible unless an active filter excludes it.
            if ($('review-filter').value && !filtered().some(item => item.file === row.file)) render();
        });
        choice.dataset.status = status; choice.className = status; statuses.append(choice);
    }
    statuses.append(button('Clear review', () => { delete reviews[row.file]; save(); updateFeedback(); render(); }));
    editor.append(statuses);
    const heading = el('div'); heading.className = 'cell-heading';
    heading.append(el('span', 'Affected roles (optional)'), button('Clear roles', () => {
        editReview(row, {cells: []});
        for (const box of picker.querySelectorAll('input')) box.checked = false;
    }));
    editor.append(heading);
    const picker = el('div'); picker.className = 'cell-picker';
    row.cells.forEach((description, index) => {
        const label = el('label'), box = el('input'); box.type = 'checkbox';
        box.checked = reviewFor(row).cells.includes(index + 1);
        box.setAttribute('aria-label', description); label.title = description;
        box.onchange = () => {
            const current = reviewFor(row), cells = new Set(current.cells);
            if (box.checked) cells.add(index + 1); else cells.delete(index + 1);
            editReview(row, {cells: [...cells].sort((a, b) => a - b), status: current.status === 'unreviewed' ? 'unsure' : current.status});
            refreshStatus();
        };
        label.append(box, document.createTextNode(row.roles[index].id)); picker.append(label);
    });
    editor.append(picker);
    const notesLabel = el('label', 'Notes'), notes = el('textarea');
    notes.rows = 3; notes.maxLength = 20000; notes.placeholder = 'Describe what looks wrong and the expected connection…';
    notes.value = reviewFor(row).notes; notes.setAttribute('aria-label', 'Notes for ' + row.file);
    notes.oninput = () => {
        const current = reviewFor(row);
        editReview(row, {notes: notes.value, status: current.status === 'unreviewed' && notes.value.trim() ? 'unsure' : current.status});
        refreshStatus();
    };
    notesLabel.append(notes); editor.append(notesLabel); refreshStatus();
    return editor;
}
function render() {
    const list = filtered(), size = Number($('page-size').value), pages = Math.max(1, Math.ceil(list.length / size));
    page = Math.max(0, Math.min(page, pages - 1));
    $('cards').replaceChildren(); $('cards').className = size === 1 ? 'single' : '';
    $('status').textContent = `${list.length} matching screenshots; ${list.filter(row => row.exists).length} available`;
    $('page').textContent = (size === 1 ? 'Screenshot ' : 'Page ') + (list.length ? page + 1 : 0) + ' / ' + (list.length ? pages : 0);
    $('prev').disabled = page === 0; $('next').disabled = page === pages - 1;
    if (!list.length) $('cards').append(el('p', 'No screenshots match. Change or clear the filters above.'));
    for (const row of list.slice(page * size, (page + 1) * size)) {
        const card = el('article'); card.id = row.name;
        card.append(el('h2', row.title + ' · ' + row.roles.map(role => role.id + '=' + role.material).join(' / ')));
        const raw = el('a', row.file); raw.href = row.file; raw.target = '_blank'; raw.rel = 'noopener'; card.append(raw);
        if (row.exists) {
            const frame = el('div'); frame.className = 'screenshot-frame';
            const image = el('img'); image.src = row.file; image.alt = row.title; image.loading = 'lazy';
            image.onerror = () => { image.alt = 'Screenshot could not load: ' + row.file; };
            frame.append(image);
            card.append(frame);
        } else { const warning = el('div', 'Missing screenshot'); warning.className = 'missing'; card.append(warning); }
        if (size !== 1) card.append(button('Focus screenshot and review', () => focusRow(row)));
        const detail = el('details'), labels = el('ol');
        detail.append(el('summary', `Block roles (${row.roles.length} blocks)`));
        row.cells.forEach(description => labels.append(el('li', description))); detail.append(labels); card.append(detail);
        card.append(reviewEditor(row, card)); $('cards').append(card);
    }
}
function download(content, name, mime) {
    const url = URL.createObjectURL(new Blob([content], {type: mime})), link = el('a');
    link.href = url; link.download = name; document.body.append(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function exportName(extension) { return captureId.replace(/[^a-z0-9_.-]/gi, '_') + '-connection-feedback.' + extension; }
$('copy-report').onclick = async () => {
    const report = reportText(); $('feedback-report').value = report;
    try {
        await navigator.clipboard.writeText(report);
        $('transfer-status').textContent = 'Feedback copied. Paste it into the chat.';
    } catch (_) {
        $('report-details').open = true; $('feedback-report').focus(); $('feedback-report').select();
        $('transfer-status').textContent = 'Clipboard access was unavailable. The report is selected below; press Ctrl+C (or Command+C), then paste it into the chat.';
    }
};
$('export-text').onclick = () => { download(reportText(), exportName('txt'), 'text/plain;charset=utf-8'); $('transfer-status').textContent = 'Text feedback downloaded.'; };
$('export-json').onclick = () => {
    const includeAll = $('export-all').checked;
    download(JSON.stringify(payload(includeAll), null, 2), exportName('json'), 'application/json');
    $('transfer-status').textContent = includeAll ? 'All reviews exported, including OK statuses. Import this JSON to restore them.' : 'Wrong, Unsure and Skip feedback exported. Enable “Include all reviews” for a complete backup.';
};
$('import-reviews').onclick = () => $('import-file').click();
$('import-file').onchange = async () => {
    const file = $('import-file').files[0];
    if (!file) return;
    try {
        const imported = JSON.parse(await file.text());
        if (imported.schema !== 'vandorlabs-connection-review-v1') throw new Error('Unsupported review file format.');
        if (imported.captureId !== captureId) throw new Error(`This file is for capture “${imported.captureId}”, not “${captureId}”. Open that capture’s reviewer to import it.`);
        const cleaned = readReviews(imported.reviews), count = Object.keys(cleaned).length;
        if (!count) throw new Error('No valid screenshot reviews for this capture were found.');
        Object.assign(reviews, cleaned); save(); updateFeedback(); render();
        $('transfer-status').textContent = `${count} reviews imported. Matching screenshot reviews were replaced; other reviews were kept.`;
    } catch (error) { $('transfer-status').textContent = 'Import failed: ' + error.message; }
    $('import-file').value = '';
};
$('reset-reviews').onclick = () => {
    if (!window.confirm(`Clear all saved reviews for capture “${captureId}”? Export JSON first if you want to keep a backup.`)) return;
    reviews = Object.create(null); save(); updateFeedback(); render();
    $('transfer-status').textContent = 'Reviews cleared for this capture.';
};
for (const id of ['kind', 'family', 'direction', 'upper-lower', 'material', 'review-filter', 'search', 'missing', 'page-size']) $(id).addEventListener('input', () => { page = 0; render(); });
$('feedback-filter').addEventListener('input', updateFeedback);
function move(direction) {
    if ((direction < 0 && $('prev').disabled) || (direction > 0 && $('next').disabled)) return;
    page += direction; render(); $('cards').scrollIntoView({block: 'start'});
}
$('prev').onclick = () => move(-1); $('next').onclick = () => move(1);
document.addEventListener('keydown', event => {
    if (event.altKey && ['ArrowLeft', 'ArrowRight'].includes(event.key)) { event.preventDefault(); move(event.key === 'ArrowLeft' ? -1 : 1); }
});
$('capture-label').textContent = captureId;
for (const [id, key] of [['family', 'family'], ['kind', 'topology'], ['direction', 'facing'], ['upper-lower', 'upper_lower']]) {
    if (new Set(rows.map(row => row[key])).size === 1) $(id).hidden = true;
}
storageMessage(); updateFeedback(); render();
