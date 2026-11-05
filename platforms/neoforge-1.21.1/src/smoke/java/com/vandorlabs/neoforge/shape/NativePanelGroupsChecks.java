package com.vandorlabs.neoforge.shape;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Independent metadata and irregular-plane goldens over the actual bounded traversal. */
public final class NativePanelGroupsChecks {
    private static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        for (int a : new int[]{0, 7, 13}) for (int b : new int[]{0, 7, 13})
            for (int la = 0; la < 3; la++) for (int lb = 0; lb < 3; lb++) {
                require(NativePanelGroups.Mode.APERTURE.sameSettings(a, la, true, 3,
                        b, lb, true, 3), "artwork/layout must not split apertures");
                require(!NativePanelGroups.Mode.APERTURE.sameSettings(a, la, true, 3,
                        b, lb, false, 3), "both aperture joins required");
                require(!NativePanelGroups.Mode.APERTURE.sameSettings(a, la, true, 3,
                        b, lb, true, 2), "different aperture shapes must split");
                require(NativePanelGroups.Mode.MATERIAL.sameSettings(a, la, false, 3,
                        b, lb, true, 2) == (a == b && la == lb), "material query remains independent");
            }
        for(Direction right:Direction.Plane.HORIZONTAL){BlockPos origin=new BlockPos(-17,80,29);Set<BlockPos> oriented=new HashSet<>();for(int y=0;y<2;y++)for(int x=0;x<3;x++)oriented.add(origin.relative(right,x).above(y));var result=NativePanelGroups.capture(origin,right,true,pos->oriented.contains(pos)?Boolean.TRUE:null);for(int y=0;y<2;y++)for(int x=0;x<3;x++){var layout=result.layouts().get(origin.relative(right,x).above(y).asLong());require(layout.column()==x&&layout.row()==y&&layout.columns()==3&&layout.rows()==2,"four-facing signed-axis rectangle golden");}}
        Set<BlockPos> rectangle = new HashSet<>();
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) rectangle.add(new BlockPos(x, y, 0));
        var capture = NativePanelGroups.capture(BlockPos.ZERO, Direction.EAST, true,
                pos -> rectangle.contains(pos) ? Boolean.TRUE : null);
        require(!capture.capped() && capture.layouts().size() == 6, "whole rectangle");
        for (var entry : capture.layouts().entrySet()) {
            var pos = BlockPos.of(entry.getKey()); var layout = entry.getValue();
            require(layout.column() == pos.getX() && layout.row() == pos.getY()
                    && layout.columns() == 3 && layout.rows() == 2, "rectangle coordinates");
        }
        Set<BlockPos> l = Set.of(BlockPos.ZERO, new BlockPos(1,0,0), new BlockPos(0,1,0));
        var irregular = NativePanelGroups.capture(BlockPos.ZERO, Direction.EAST, true,
                pos -> l.contains(pos) ? Boolean.TRUE : null);
        require(irregular.layouts().get(BlockPos.ZERO.asLong()).columns() == 2
                && irregular.layouts().get(new BlockPos(0,1,0).asLong()).columns() == 1,
                "largest-first L partition golden");
        require(NativePanelGroups.neighborMask(BlockPos.ZERO,Direction.EAST,l::contains)==10,
                "direct rail contacts cross rectangle partitions");
        var capped = NativePanelGroups.capture(BlockPos.ZERO, Direction.EAST, true,
                pos -> pos.getY() == 0 && pos.getZ() == 0 && pos.getX() >= 0 && pos.getX() <= 4096
                        ? Boolean.TRUE : null);
        require(capped.capped() && capped.layouts().size() == 4096
                && capped.layouts().values().stream().allMatch(NativePanelLayout.UNIT::equals),
                "cap publishes UNIT without window geometry");
        require(NativePanelGroups.neighborMask(new BlockPos(4095,0,0),Direction.EAST,
                pos->pos.getY()==0&&pos.getZ()==0&&pos.getX()>=0&&pos.getX()<=4096)==3,
                "capped UNIT still hides directly compatible outside-group neighbor");
        var descriptor = NativePortholeLayout.unit(3);
        require(descriptor.pane() == NativePanelLayout.UNIT && descriptor.shape() == 3
                && descriptor.extraBorder() == 0, "descriptor retains shape without outline");
        System.out.println("Native panel groups PASS independent artwork/aperture policies, rectangle/L goldens, 4096 cap; descriptors only");
    }
}
