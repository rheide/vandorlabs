package com.vandorlabs.redstone;

import com.vandorlabs.persistence.PrimitiveData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Screen-row configuration and lifecycle shared by native screens and future carried adapters. */
public final class RedstoneScreenModel<R extends RedstoneControlRow> {
    public interface Owner<R extends RedstoneControlRow> {
        R createRow(String label, ChannelList channels);
        int maxRows();
        boolean validHousing(int choice);
        int housing();
        void housing(int choice);
        boolean hasWorld();
        boolean authoritative();
        boolean canPulse();
        boolean publishedLocally();
        long serverTime();
        void dirty();
        void changed();
        void register(R row);
        void unregister(R row);
        void inputChanged(R row);
        void latchLevelChanged(R row, int level);
        boolean allPowered(ChannelList channels);
        void activatePulses();
    }
    private final Owner<R> owner;
    private boolean enabled;
    private String title = RedstoneScreenPolicy.DEFAULT_TITLE;
    private List<R> rows = new ArrayList<>(), registered = new ArrayList<>();
    private List<R> view = Collections.unmodifiableList(rows);
    public RedstoneScreenModel(Owner<R> owner) { this.owner = java.util.Objects.requireNonNull(owner); }
    public boolean enabled() { return enabled; }
    public String title() { return title; }
    public List<R> rows() { return view; }
    public int maxRows() { return owner.maxRows(); }
    public void writeMetadata(PrimitiveData tag) { tag.putBoolean("Enabled", enabled); tag.putString("Title", title); }
    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (value) refreshRows(); else unregisterRows();
        owner.changed();
    }
    public boolean configure(String heading, List<String> labels, List<ChannelList> channels, int housing, boolean enable) {
        if (!RedstoneScreenPolicy.validTitle(heading) || labels.size() > maxRows() || labels.size() != channels.size() || !owner.validHousing(housing)) return false;
        for (int i = 0; i < labels.size(); i++) if (!RedstoneScreenPolicy.validLabel(labels.get(i)) || channels.get(i) == null) return false;
        List<R> next = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            R row = owner.createRow(labels.get(i).trim(), channels.get(i));
            if (i < rows.size()) row.copyConfigurationState(rows.get(i));
            next.add(row);
        }
        enabled = enable; title = heading.trim(); rows = next; view = Collections.unmodifiableList(rows);
        owner.housing(housing);
        if (enabled) refreshRows(); else unregisterRows();
        owner.changed(); return true;
    }
    /** Option replacement retains the historical unregister/read/register order per row. */
    public void applyOptions(List<? extends PrimitiveData> options) {
        for (int i = 0; i < rows.size(); i++) {
            R row = rows.get(i); owner.unregister(row); row.readOptions(options.get(i));
            if (enabled) owner.register(row);
            if (owner.authoritative()) row.settle();
        }
        owner.changed();
    }
    public void selectSegment(int index, int segment) {
        if (!owner.authoritative() || !enabled || index < 0 || index >= rows.size()) return;
        R row = rows.get(index);
        if (!row.slider || segment < 0 || segment >= row.segments()) return;
        owner.latchLevelChanged(row, row.segmentValue(segment));
    }
    public void toggleRow(int index) {
        if (!owner.authoritative() || !enabled || index < 0 || index >= rows.size()) return;
        R row = rows.get(index);
        if (row.channels.isEmpty() || row.slider) return;
        if (row.pulse) { pulseRow(index); return; }
        owner.latchLevelChanged(row, owner.allPowered(row.channels) ? 0 : 15);
    }
    public void pulseRow(int index) {
        if (!owner.canPulse() || !enabled || index < 0 || index >= rows.size()) return;
        R row = rows.get(index);
        if (!row.pulse || row.channels.isEmpty()) return;
        row.startPulse(owner.serverTime());
        if (owner.publishedLocally()) { owner.inputChanged(row); owner.activatePulses(); }
    }
    public boolean expirePulses(long now) {
        boolean active = false;
        for (R row : rows) if (row.pulseUntil() > 0) {
            if (row.expirePulse(now)) owner.inputChanged(row); else active = true;
        }
        return active;
    }
    public boolean hasPulses() { for (R row : rows) if (row.pulse && row.pulseUntil() > 0) return true; return false; }
    public void finishLoading() {
        limitRows();
        if (enabled) refreshRows(); else unregisterRows();
        if (hasPulses()) owner.activatePulses();
    }
    public void unregisterRows() { for (R row : registered) owner.unregister(row); registered.clear(); }
    private void refreshRows() {
        if (!owner.authoritative() || !enabled) return;
        // The unchanged bank must never temporarily lose its only source during an edit.
        for (R row : rows) owner.register(row);
        for (R row : registered) if (!rows.contains(row)) owner.unregister(row);
        registered = new ArrayList<>(rows);
        for (R row : rows) row.settle();
    }
    private void limitRows() {
        if (rows.size() > maxRows()) {
            rows = new ArrayList<>(rows.subList(0, maxRows())); view = Collections.unmodifiableList(rows); owner.dirty();
        }
    }
    /** Local-only deserialization; registration is deferred to finishLoading by the host. */
    public void restore(boolean enabled, String heading, List<R> saved) {
        this.enabled = enabled; title = RedstoneScreenPolicy.validTitle(heading) ? heading : RedstoneScreenPolicy.DEFAULT_TITLE;
        List<R> next = new ArrayList<>();
        for (int i = 0; i < Math.min(RedstoneScreenPolicy.MAX_ROWS, saved.size()); i++) {
            R row = saved.get(i); if (RedstoneScreenPolicy.validLabel(row.label)) next.add(row);
        }
        rows = next; view = Collections.unmodifiableList(rows);
        if (owner.hasWorld()) limitRows();
    }
}
