package com.vandorlabs.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.vandorlabs.navigation.NavigationLayout.Cell;
import com.vandorlabs.navigation.NavigationLayout.Facing;
import com.vandorlabs.navigation.NavigationLayout.Kind;
import com.vandorlabs.navigation.NavigationLayout.Layout;
import com.vandorlabs.navigation.NavigationLayout.Screen;
import com.vandorlabs.navigation.NavigationLayout.Tile;
import com.vandorlabs.navigation.NavigationLayout.Vec3;

/** Standalone behavior tests. Run main; no JUnit or Minecraft dependencies. */
public final class NavigationLayoutTest {
    private static int checks;
    private static void require(boolean ok, String why) {
        checks++; if (!ok) throw new AssertionError(why);
    }
    private static void close(double a, double b, String why) {
        require(Math.abs(a - b) < 1e-9, why + ": " + a + " != " + b);
    }
    private static List<Cell> rectangle(Facing f, int w, int d) {
        List<Cell> cells = new ArrayList<Cell>();
        for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            Vec3 p = f.rotateDirection(new Vec3(x, 0, z));
            cells.add(new Cell(111 + (int)p.x, 64, -37 + (int)p.z));
        }
        return cells;
    }
    private static void rejected(Runnable operation, String label) {
        try { operation.run(); throw new AssertionError("Accepted " + label); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void verify(Kind kind, Facing f, int w, int d) {
        List<Cell> cells = rectangle(f, w, d);
        Layout layout = NavigationLayout.build(kind, f, cells, true);
        require(layout.width == w && layout.depth == d, "Oriented dimensions");
        int chipCount = 0;
        for (Tile t : layout.tiles.values()) {
            if (t.chip) {
                chipCount++;
                require(t.localX == (w - 1) / 2 && t.localZ == 0, "Front centre owner");
                require((t.connections & NavigationLayout.NORTH) == 0, "Chip faces outside");
                require(t.cell.equals(layout.chipOwner), "Owner record matches");
            }
            require(((t.connections & NavigationLayout.WEST) != 0) == (t.localX > 0), "West join");
            require(((t.connections & NavigationLayout.EAST) != 0) == (t.localX < w - 1), "East join");
            require(((t.connections & NavigationLayout.NORTH) != 0) == (t.localZ > 0), "North join");
            require(((t.connections & NavigationLayout.SOUTH) != 0) == (t.localZ < d - 1), "South join");
            Vec3 centre = layout.moduleAnchor(t.cell, new Vec3(8, 8, 8));
            close(centre.x, t.cell.x + 0.5, "Cell centre X");
            close(centre.y, t.cell.y + 0.5, "Cell centre Y");
            close(centre.z, t.cell.z + 0.5, "Cell centre Z");
        }
        require(chipCount == 1, "Exactly one chip mount");
        Screen s = layout.screen();
        close(s.widthBlocks, w - 0.25, "Merged display width");
        close(s.heightBlocks, kind == Kind.FLAT ? d - 0.25 : Math.sqrt(180) / 16.0, "Display height");
        Vec3 expectedNormal = f.rotateDirection(kind == Kind.FLAT ? new Vec3(0, 1, 0)
                : new Vec3(0, 2 / Math.sqrt(5), -1 / Math.sqrt(5)));
        close(s.normal.x, expectedNormal.x, "Normal X");
        close(s.normal.y, expectedNormal.y, "Normal Y");
        close(s.normal.z, expectedNormal.z, "Normal Z");
        Vec3 overlay = s.overlayPoint(0, 1).minus(s.bottomLeft);
        close(overlay.x, s.normal.x * NavigationLayout.OVERLAY_OFFSET_BLOCKS, "Offset X");
        close(overlay.y, s.normal.y * NavigationLayout.OVERLAY_OFFSET_BLOCKS, "Offset Y");
        close(overlay.z, s.normal.z * NavigationLayout.OVERLAY_OFFSET_BLOCKS, "Offset Z");
        Collections.reverse(cells);
        Layout reversed = NavigationLayout.build(kind, f, cells, true);
        require(reversed.chipOwner.equals(layout.chipOwner), "Input order must not change owner");
        require(new ArrayList<Cell>(reversed.tiles.keySet()).equals(new ArrayList<Cell>(layout.tiles.keySet())),
                "Input order must not change assignment order");
    }
    public static void main(String[] args) {
        for (Facing f : Facing.values()) {
            for (int w = 1; w <= 8; w++) for (int d = 1; d <= 8; d++) verify(Kind.FLAT, f, w, d);
            for (int w = 1; w <= 16; w++) verify(Kind.ANGLED, f, w, 1);
        }
        rejected(() -> NavigationLayout.build(Kind.FLAT, Facing.NORTH,
                rectangle(Facing.NORTH, 1, 1), false), "incomplete component");
        rejected(() -> NavigationLayout.build(Kind.ANGLED, Facing.NORTH,
                rectangle(Facing.NORTH, 2, 2), true), "angled depth rows");
        final List<Cell> hole = rectangle(Facing.NORTH, 3, 3); hole.remove(4);
        rejected(() -> NavigationLayout.build(Kind.FLAT, Facing.NORTH, hole, true), "central hole");
        final List<Cell> lShape = rectangle(Facing.NORTH, 2, 2); lShape.remove(3);
        rejected(() -> NavigationLayout.build(Kind.FLAT, Facing.NORTH, lShape, true), "L shape");
        final List<Cell> split = new ArrayList<Cell>(); split.add(new Cell(0, 0, 0)); split.add(new Cell(2, 0, 0));
        rejected(() -> NavigationLayout.build(Kind.FLAT, Facing.NORTH, split, true), "disconnected members");
        final List<Cell> duplicate = rectangle(Facing.NORTH, 1, 1); duplicate.add(duplicate.get(0));
        rejected(() -> NavigationLayout.build(Kind.FLAT, Facing.NORTH, duplicate, true), "duplicate members");
        final List<Cell> levels = rectangle(Facing.NORTH, 1, 1); levels.add(new Cell(111, 65, -36));
        rejected(() -> NavigationLayout.build(Kind.FLAT, Facing.NORTH, levels, true), "mixed heights");
        rejected(() -> NavigationLayout.build(Kind.FLAT, Facing.NORTH,
                rectangle(Facing.NORTH, 17, 16), true), "oversize component");
        System.out.println("PASS: " + checks + " assertions; 320 layouts across four facings.");
    }
}
