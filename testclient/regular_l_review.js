'use strict';
const $ = id => document.getElementById(id);
const storageKey = 'vandorlabs.connection-review.v1:' + encodeURIComponent(captureId) + ':' + encodeURIComponent(reportScope);
const knownFiles = new Set(rows.map(row => row.file));
let reviews = Object.create(null), storageAvailable = true;
try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || 'null');
    if (saved && saved.captureId === captureId && saved.schema === 'vandorlabs-connection-review-v1') {
        for (const [file, review] of Object.entries(saved.reviews || {})) {
            if (knownFiles.has(file) && review && review.status === 'wrong')
                reviews[file] = {status: 'wrong', cells: [], notes: typeof review.notes === 'string' ? review.notes : ''};
        }
    }
} catch (_) { storageAvailable = false; }
function payload() {
    return {schema: 'vandorlabs-connection-review-v1', captureId, defaultStatus: 'ok',
        scope: reportScope, displayedFiles: rows.map(row => row.file), exportedAt: new Date().toISOString(), reviews};
}
function reportText() {
    const wrong = rows.filter(row => reviews[row.file]);
    const lines = [reviewTitle + ' feedback', 'Capture: ' + captureId, reportScope,
        `Wrong: ${wrong.length}; assumed correct: ${rows.length - wrong.length}`,
        'Only displayed cases not listed below are assumed correct.', ''];
    for (const row of wrong) {
        lines.push('WRONG | ' + row.file, row.title,
            row.cells.join(' / '), 'Placement order: ' + row.placement_order.join(' → '));
        if (reviews[row.file].notes.trim()) lines.push('Notes: ' + reviews[row.file].notes.trim());
        lines.push('');
    }
    if (!wrong.length) lines.push('No wrong connections marked.');
    return lines.join('\n');
}
function refresh() {
    const count = Object.keys(reviews).length;
    $('counts').textContent = `${count} wrong · ${rows.length - count} assumed correct`;
    $('feedback-report').value = reportText();
    $('storage-status').textContent = storageAvailable ? 'Marks are saved automatically in this browser.'
        : 'Browser storage is unavailable. Copy or export your feedback before closing.';
}
function save() {
    try { localStorage.setItem(storageKey, JSON.stringify(payload())); storageAvailable = true; }
    catch (_) { storageAvailable = false; }
    refresh();
}
function el(tag, text) {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    return element;
}
const cards = new Map();
function updateCard(row) {
    const {card, toggle, badge, notesLabel, notes} = cards.get(row.file), wrong = !!reviews[row.file];
    card.dataset.wrong = String(wrong);
    toggle.setAttribute('aria-pressed', String(wrong));
    toggle.setAttribute('aria-label', `${row.title}: ${wrong ? 'marked wrong; click to undo' : 'assumed correct; click to mark wrong'}`);
    badge.textContent = wrong ? 'Wrong — click to undo' : 'Correct — click if wrong';
    notesLabel.hidden = !wrong;
    notes.value = wrong ? reviews[row.file].notes : '';
}
rows.forEach((row, index) => {
    const card = el('article'); card.id = row.name;
    card.append(el('h2', `${index + 1}. ${row.roles.map(role => role.material).join(' + ')}`));
    const description = row.family === 'diagonal' ? row.title : row.title.split(' · ').at(-1);
    const caption = el('p', description + ' · place ' + row.placement_order.join(' → '));
    caption.className = 'caption'; card.append(caption);
    if (row.changed_since_accepted) {
        const marker = el('p'); marker.className = 'caption change-marker';
        const link = el('a', 'Changed since accepted — compare'); link.href = row.regression_link;
        marker.append(link); card.append(marker);
    }
    const toggle = el('button'); toggle.type = 'button'; toggle.className = 'image-toggle';
    const image = el('img'); image.src = row.file; image.alt = row.title + ' · ' + row.cells.join(' / '); image.loading = 'lazy';
    if (row.preview_style) { image.style.width = row.preview_style.width; image.style.transform = row.preview_style.transform; }
    const badge = el('span'); badge.className = 'badge'; toggle.append(image, badge);
    toggle.onclick = () => {
        if (reviews[row.file]) delete reviews[row.file];
        else reviews[row.file] = {status: 'wrong', cells: [], notes: '', updatedAt: new Date().toISOString()};
        updateCard(row); save();
    };
    card.append(toggle);
    const footer = el('div'); footer.className = 'card-footer';
    footer.append(el('span', row.roles.map(role => role.id + '=' + role.material).join(' / ')));
    const raw = el('a', 'Full size'); raw.href = row.file; raw.target = '_blank'; raw.rel = 'noopener'; footer.append(raw); card.append(footer);
    if (row.secondary_file) {
        let opposite = false;
        const view = el('button', 'View opposite side'); view.type = 'button'; view.className = 'view-switch';
        view.onclick = () => {
            opposite = !opposite;
            image.src = opposite ? row.secondary_file : row.file;
            const style = opposite ? row.secondary_preview_style : row.preview_style;
            image.style.width = style ? style.width : '150%';
            image.style.transform = style ? style.transform : 'translate(-50%, -50%)';
            image.alt = row.title + (opposite ? ' · opposite lower oblique view' : ' · primary view');
            raw.href = image.src; view.textContent = opposite ? 'View primary side' : 'View opposite side';
        };
        footer.append(view);
    }
    const notesLabel = el('label', 'Optional note'), notes = el('textarea'); notesLabel.className = 'notes';
    notes.maxLength = 20000; notes.setAttribute('aria-label', 'Note for case ' + (index + 1));
    notes.oninput = () => { if (reviews[row.file]) { reviews[row.file].notes = notes.value; save(); } };
    notesLabel.append(notes); card.append(notesLabel);
    cards.set(row.file, {card, toggle, badge, notesLabel, notes}); updateCard(row); $('cards').append(card);
});
function download(content, extension, mime) {
    const url = URL.createObjectURL(new Blob([content], {type: mime})), link = el('a');
    link.href = url; link.download = captureId + '-connection-feedback.' + extension;
    document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
$('copy-report').onclick = async () => {
    try { await navigator.clipboard.writeText(reportText()); $('transfer-status').textContent = 'Copied. Paste the feedback into the chat.'; }
    catch (_) {
        $('report-details').open = true; $('feedback-report').focus(); $('feedback-report').select();
        $('transfer-status').textContent = 'Feedback selected below. Press Ctrl+C, then paste it into the chat.';
    }
};
$('export-text').onclick = () => download(reportText(), 'txt', 'text/plain;charset=utf-8');
$('export-json').onclick = () => download(JSON.stringify(payload(), null, 2), 'json', 'application/json');
$('reset-reviews').onclick = () => {
    if (!Object.keys(reviews).length || window.confirm('Clear all wrong marks?')) {
        reviews = Object.create(null); rows.forEach(updateCard); save();
    }
};
refresh();
