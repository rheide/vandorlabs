package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Moving craft must carry the same hollow canopy surfaces as placed assemblies. */
public final class VehicleCanopyRuntimeChecks {
    private static final BlockPos ANCHOR=new BlockPos(8,2,8);
    private static void require(boolean condition,String what){if(!condition)throw new IllegalStateException("vehicle canopy: "+what);}
    private static VehicleStructure fixture(BlockCanopy block,EnumFacing facing,int constituents,float progress) {
        return fixture(block,facing,constituents,progress,16,71);
    }
    private static VehicleStructure fixture(BlockCanopy block,EnumFacing facing,int constituents,float progress,int depth,int scale) {
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);
        for(int column=0;column<constituents;column++) {
            BlockPos part=ANCHOR.offset(facing.rotateY(),column);
            boolean configurable=com.vandorlabs.canopy.LargeCanopySettings.supports(block.kind);
            for(BlockPos pos:block.cells(part,facing,configurable?Math.max(1,(depth+15)/16):block.length)) {
                TileEntityCanopy tile=new TileEntityCanopy();tile.setPos(pos);tile.anchor=part;
                if(constituents>1)tile.pair=ANCHOR;
                tile.progress=tile.previous=progress;tile.targetOpen=progress>0;
                NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setInteger("CanopyWidth",constituents>1?constituents:block.width);
                if(configurable){tag.setInteger("CanopyDepth",depth);tag.setInteger("CanopyScale",scale);}
                cells.add(new VehicleStructure.Cell(pos,state,tag));
            }
        }
        return new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1);
    }
    public static void check(World world) {
        int max=0;
        for(String id:new String[]{"regular_canopy_glass","opening_slide_1x2","opening_hinge_1x2","space_shuttle_3x3","space_bubble_wedge_3x3","large_front_canopy_glass"}) {
            BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+id);
            for(EnumFacing facing:EnumFacing.HORIZONTALS) {
                VehicleStructure structure=fixture(block,facing,block.opening()?3:1,0);
                for(int turn=0;turn<4;turn++) {
                    VehicleWorld view=new VehicleWorld(world,structure);VehicleCollision hull=new VehicleCollision(view);
                    TileEntityCanopy owner=null;
                    for(VehicleStructure.Cell cell:structure.cells)if(view.getTileEntity(cell.pos) instanceof TileEntityCanopy && ((TileEntityCanopy)view.getTileEntity(cell.pos)).draws())owner=(TileEntityCanopy)view.getTileEntity(cell.pos);
                    require(owner!=null,"drawing owner exists");
                    AxisAlignedBB surface=owner.boxes().get(0).grow(1e-6);
                    require(hull.boxes.stream().anyMatch(box->box.intersects(surface)),"canopy contributes local hull surfaces "+id);
                    require(hull.boxes.size()<=8192,"merged hull respects limit "+id);max=Math.max(max,hull.boxes.size());
                    if(id.equals("regular_canopy_glass")) {
                        AxisAlignedBB interior=new AxisAlignedBB(owner.getPos()).shrink(.3);
                        require(hull.pose(0).stream().noneMatch(box->box.intersects(interior)),"canopy cabin stays hollow");
                    }
                    structure=VehicleRotation.rotate(structure,view,1);
                }
                if(block.width==3) {
                    VehicleStructure full=fixture(block,facing,1,0);List<VehicleStructure.Cell> roots=new ArrayList<>();
                    for(VehicleStructure.Cell cell:full.cells)if(cell.pos.equals(BlockPos.ORIGIN)||cell.pos.equals(ANCHOR))roots.add(cell);
                    VehicleStructure anchorsOnly=new VehicleStructure(roots,BlockPos.ORIGIN,EnumFacing.NORTH,1);
                    require(new VehicleCollision(new VehicleWorld(world,full)).boxes.toString().equals(new VehicleCollision(new VehicleWorld(world,anchorsOnly)).boxes.toString()),"proxy cells do not duplicate canopy hull "+id);
                }
                if(block.opening()) {
                    VehicleCollision closed=new VehicleCollision(new VehicleWorld(world,fixture(block,facing,3,0))),open=new VehicleCollision(new VehicleWorld(world,fixture(block,facing,3,1)));
                    require(!closed.boxes.toString().equals(open.boxes.toString()),"captured shell pose changes hull "+id);
                }
            }
        }
        BlockCanopy largest=(BlockCanopy)Block.getBlockFromName("vandorlabs:large_front_canopy_glass_5x5");
        require(largest!=null,"configurable five-wide block registered");
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth:new int[]{0,80})for(int scale:new int[]{10,71,100}) {
            VehicleStructure structure=fixture(largest,facing,1,0,depth,scale);VehicleWorld view=new VehicleWorld(world,structure);
            VehicleCollision hull=new VehicleCollision(view);max=Math.max(max,hull.boxes.size());
            require(hull.boxes.size()<=8192,"five-wide maximum depth hull fits collision budget");
            TileEntityCanopy owner=(TileEntityCanopy)view.getTileEntity(ANCHOR);
            require(hull.boxes.stream().anyMatch(box->box.intersects(owner.boxes().get(0).grow(1e-6))),"configurable large surfaces join vehicle hull");
        }
        System.out.println("[vandorlabs][reprolab] vehicle-canopy-hull PASS local-surfaces hollow-interior proxy-dedup rotated-poses max-merged-boxes="+max);
    }
}
