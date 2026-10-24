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
    private static void mixed(){
        for(int code=0;code<256;code++){
            java.util.Map<Cell,Facing> input=new java.util.LinkedHashMap<>();
            for(int i=0;i<4;i++)input.put(new Cell(i%2,64,i/2),Facing.values()[(code>>(i*2))&3]);
            Layout layout=NavigationLayout.mixedFlat(input,true);
            int owners=0;double area=0;
            for(Tile tile:layout.tiles.values()){
                if(tile.chip)owners++;
                NavigationLayout.DisplayPatch patch=NavigationLayout.displayPatch(Kind.FLAT,input.get(tile.cell),tile.cell,tile.connections,layout.facing,layout.originCell,layout.width,layout.depth);
                Vec3[] corners=patch.points;
                for(int i=0;i<4;i++){
                    Vec3 reconstructed=layout.screen().overlayPoint(patch.u[i],patch.v[i]);
                    close(reconstructed.x,corners[i].x,"Continuous image U position");close(reconstructed.z,corners[i].z,"Continuous image V position");
                    require(patch.u[i]>=-1e-9&&patch.u[i]<=1+1e-9&&patch.v[i]>=-1e-9&&patch.v[i]<=1+1e-9,"Image UV bounds");
                }
                double minX=Double.MAX_VALUE,maxX=-minX,minZ=minX,maxZ=-minX;
                for(Vec3 p:corners){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);}
                close(minX,tile.cell.x+(tile.cell.x==0?.125:0),"No internal X divider");
                close(maxX,tile.cell.x+(tile.cell.x==1?.875:1),"No internal X divider");
                close(minZ,tile.cell.z+(tile.cell.z==0?.125:0),"No internal Z divider");
                close(maxZ,tile.cell.z+(tile.cell.z==1?.875:1),"No internal Z divider");
                area+=(maxX-minX)*(maxZ-minZ);
            }
            require(owners==1,"Mixed facing single owner");close(area,1.75*1.75,"Continuous screen area");
            java.util.List<Cell> reverse=new java.util.ArrayList<>(input.keySet());Collections.reverse(reverse);
            java.util.Map<Cell,Facing> reversed=new java.util.LinkedHashMap<>();for(Cell cell:reverse)reversed.put(cell,input.get(cell));
            Layout again=NavigationLayout.mixedFlat(reversed,true);
            require(layout.chipOwner.equals(again.chipOwner)&&layout.facing==again.facing,"Stable mixed owner/orientation");
        }
        java.util.Random random=new java.util.Random(62072);
        for(int sample=0;sample<100;sample++){
            java.util.Map<Cell,Facing> input=new java.util.LinkedHashMap<>();
            for(int z=0;z<9;z++)for(int x=0;x<12;x++)input.put(new Cell(x,0,z),Facing.values()[random.nextInt(4)]);
            Layout layout=NavigationLayout.mixedFlat(input,true);
            java.util.List<Cell> order=new java.util.ArrayList<>(input.keySet());Collections.shuffle(order,random);
            java.util.Map<Cell,Facing> shuffled=new java.util.LinkedHashMap<>();for(Cell cell:order)shuffled.put(cell,input.get(cell));
            Layout again=NavigationLayout.mixedFlat(shuffled,true);
            require(layout.chipOwner.equals(again.chipOwner)&&new java.util.ArrayList<>(layout.tiles.keySet()).equals(new java.util.ArrayList<>(again.tiles.keySet())),"Large mixed rectangle input order");
        }
        for(int bits=0;bits<16;bits++){
            boolean redstone=(bits&1)!=0,security=(bits&2)!=0,power=(bits&4)!=0,chip=(bits&8)!=0;
            require(NavigationScreenPolicy.visible(redstone,security,power,chip)==((!redstone||power)&&(!security||chip)),"Display gate truth table");
        }
    }
    public static void main(String[] args) {
        mixed();
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
        System.out.println("PASS: " + checks + " assertions; 320 uniform layouts, 256 exhaustive mixed layouts, 100 larger shuffled layouts and 16 display gate combinations.");
    }
}
