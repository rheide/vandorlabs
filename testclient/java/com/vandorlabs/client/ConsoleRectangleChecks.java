package com.vandorlabs.client;

import com.vandorlabs.shipsystems.ConsoleConnections;
import com.vandorlabs.shipsystems.MountFrame;
import com.vandorlabs.shipsystems.ShipSystemMesh;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.tiles.TileEntityShipSystem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.AxisAlignedBB;
import java.util.*;

/** Filled rectangles, rotations, gaps and saved two-dimensional group membership. */
public final class ConsoleRectangleChecks {
    private static Set<BlockPos> grid(EnumFacing facing, int width, int depth) {
        return grid(facing,EnumFacing.UP,width,depth);
    }
    private static Set<BlockPos> grid(EnumFacing facing,EnumFacing mount,int width,int depth) {
        Set<BlockPos> cells=new HashSet<>();
        for(int z=0;z<depth;z++)for(int x=0;x<width;x++)
            cells.add(new MountFrame(facing,mount).cell(BlockPos.ORIGIN,x,0,z));
        return cells;
    }
    private static void coverage(Set<BlockPos> cells, EnumFacing facing) {
        coverage(cells,facing,EnumFacing.UP);
    }
    private static void coverage(Set<BlockPos> cells,EnumFacing facing,EnumFacing mount) {
        Set<BlockPos> covered=new HashSet<>();
        for(ConsoleConnections.Rectangle r:ConsoleConnections.rectangles(cells,facing,mount)) {
            if(r.width<1 || r.width>3 || r.depth<1 || r.depth>3)throw new AssertionError("Invalid size");
            for(int z=0;z<r.depth;z++)for(int x=0;x<r.width;x++) {
                BlockPos p=r.cell(facing,x,z);
                if(!cells.contains(p) || !covered.add(p))throw new AssertionError("Gap or duplicate member");
            }
        }
        if(!covered.equals(cells))throw new AssertionError("Missing members");
    }
    public static void main(String[] args) {
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityShipSystem.class,
                new net.minecraft.util.ResourceLocation("vandorlabs", "console_rectangle_check"));
        for(EnumFacing mount:EnumFacing.values())for(EnumFacing facing:EnumFacing.HORIZONTALS) {
            MountFrame frame=new MountFrame(facing,mount);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<3;z++) {
                BlockPos cell=frame.cell(BlockPos.ORIGIN,x,y,z);
                Vec3d actual=frame.point(new Vec3d(x+.5,y+.5,z+.5));
                if(actual.squareDistanceTo(new Vec3d(cell).addVector(.5,.5,.5))>1e-10)
                    throw new AssertionError("Rendering/occupancy frame mismatch");
            }
            Vec3d foot=frame.point(new Vec3d(.5,0,.5));
            Vec3d expectedFoot=new Vec3d(.5,.5,.5).subtract(new Vec3d(mount.getDirectionVec()).scale(.5));
            if(foot.squareDistanceTo(expectedFoot)>1e-10)throw new AssertionError("Feet do not meet support face");
            if(mount==EnumFacing.UP && frame.point(new Vec3d(1.2,2.3,3.4)).squareDistanceTo(
                    CanopyMesh.rotate(new Vec3d(1.2,2.3,3.4),facing))>1e-10)throw new AssertionError("Legacy floor rotation changed");
            for(String model:ShipSystemMesh.DIMENSIONS.keySet()) {
                int[] dimensions=ShipSystemMesh.DIMENSIONS.get(model);
                AxisAlignedBB envelope=new AxisAlignedBB(frame.point(Vec3d.ZERO),frame.point(new Vec3d(dimensions[0],dimensions[2],dimensions[1])));
                for(AxisAlignedBB box:ShipSystemMesh.boxes(model,facing,mount,BlockPos.ORIGIN))
                    if(box.minX<envelope.minX-1e-7 || box.minY<envelope.minY-1e-7 || box.minZ<envelope.minZ-1e-7
                            || box.maxX>envelope.maxX+1e-7 || box.maxY>envelope.maxY+1e-7 || box.maxZ>envelope.maxZ+1e-7)
                        throw new AssertionError("Collision outside rotated occupancy: "+model);
            }
            for(int width=1;width<=3;width++)for(int depth=1;depth<=3;depth++) {
                Set<BlockPos> cells=grid(facing,mount,width,depth);
                List<ConsoleConnections.Rectangle> groups=ConsoleConnections.rectangles(cells,facing,mount);
                if(groups.size()!=1 || groups.get(0).width!=width || groups.get(0).depth!=depth
                        || !groups.get(0).owner.equals(BlockPos.ORIGIN))throw new AssertionError("Rectangle did not join");
                coverage(cells,facing,mount);
                for(BlockPos removed:cells) {
                    Set<BlockPos> broken=new HashSet<>(cells);broken.remove(removed);
                    coverage(broken,facing,mount);
                }
            }
            Set<BlockPos> longRow=grid(facing,mount,6,2);
            if(ConsoleConnections.rectangles(longRow,facing,mount).size()!=2)throw new AssertionError("Long row partition");
            coverage(longRow,facing,mount);
        }
        TileEntityShipSystem original=new TileEntityShipSystem();
        original.consoleDepth=3;original.consoleRow=2;original.consoleWidth=3;original.consoleIndex=1;
        original.consoleOwner=new BlockPos(-1,0,-2);
        original.mount=EnumFacing.WEST;
        NBTTagCompound saved=original.writeToNBT(new NBTTagCompound());
        TileEntityShipSystem restored=new TileEntityShipSystem();restored.readFromNBT(saved);
        if(restored.consoleDepth!=3 || restored.consoleRow!=2 || restored.consoleIndex!=1
                || restored.mount!=EnumFacing.WEST || !restored.consoleOwner.equals(original.consoleOwner))throw new AssertionError("Saved rectangle membership");
        saved.removeTag("ConsoleDepth");saved.removeTag("ConsoleRow");restored.readFromNBT(saved);
        if(restored.consoleDepth!=1 || restored.consoleRow!=0)throw new AssertionError("Legacy row migration");
        saved.removeTag("MountFace");restored.readFromNBT(saved);
        if(restored.mount!=EnumFacing.UP)throw new AssertionError("Legacy floor mount migration");
        System.out.println("PASS: Console rectangles and all machinery collision bounds across six mount faces/four facings, gaps, regrouping and NBT migration.");
    }
}
