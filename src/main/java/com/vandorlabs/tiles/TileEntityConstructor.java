package com.vandorlabs.tiles;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Validate first, then reserve a footprint; reconstruction restores the consumed frame. */
public final class TileEntityConstructor extends TileEntity {
    private boolean changing;private IBlockState removedState;
    private int coreX,coreZ;
    private String assemblyProblem="";
    /** Facing/formed changes belong to this assembly; replacing the tile loses its detected corner. */
    @Override public boolean shouldRefresh(net.minecraft.world.World world,BlockPos pos,IBlockState before,IBlockState after){return before.getBlock()!=after.getBlock();}
    public BlockConstructorCore core(){return (BlockConstructorCore)(removedState==null?getBlockType():removedState.getBlock());}
    public EnumFacing facing(){return (removedState==null?world.getBlockState(pos):removedState).getValue(BlockVandorDirectional.FACING);}
    public boolean formed(){return removedState!=null?removedState.getValue(BlockConstructorCore.FORMED):world!=null && world.getBlockState(pos).getBlock() instanceof BlockConstructorCore && world.getBlockState(pos).getValue(BlockConstructorCore.FORMED);}
    private BlockPos rotated(int x,int y,int z,EnumFacing direction){switch(direction){case EAST:return new BlockPos(2-z,y,x);case SOUTH:return new BlockPos(core().width-1-x,y,2-z);case WEST:return new BlockPos(z,y,core().width-1-x);default:return new BlockPos(x,y,z);}}
    public BlockPos pivot(){return rotated(coreX,0,coreZ,facing());}
    public BlockPos cell(int x,int y,int z){return pos.add(rotated(x,y,z,facing()).subtract(pivot()));}
    private BlockPos candidateCell(int x,int y,int z,EnumFacing direction,int cornerX,int cornerZ){return pos.add(rotated(x,y,z,direction).subtract(rotated(cornerX,0,cornerZ,direction)));}
    public String assemblyProblem(){return assemblyProblem;}
    public boolean frame(int x,int y,int z){return y==0 || (x==0 || x==core().width-1) && (z==0 || z==2) || y==core().height-1 && (x==0 || x==core().width-1 || z==0 || z==2);}
    public boolean assemble(){
        if(world==null || world.isRemote || changing || formed())return false;
        Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));
        EnumFacing selected=null;int selectedX=0,selectedZ=0,best=Integer.MAX_VALUE;
        EnumFacing preferred=facing();
        // Prefer the placed facing, but infer the footprint rather than requiring a hidden corner convention.
        for(int turn=0;turn<4 && selected==null;turn++){
            EnumFacing direction=EnumFacing.getHorizontal((preferred.getHorizontalIndex()+turn)%4);
            for(int cornerX:new int[]{0,core().width-1})for(int cornerZ:new int[]{0,2}){
                int errors=0;String first="";
                for(int y=0;y<core().height;y++)for(int x=0;x<core().width;x++)for(int z=0;z<3;z++){
                    BlockPos p=candidateCell(x,y,z,direction,cornerX,cornerZ);if(p.equals(pos))continue;
                    boolean loaded=world.isBlockLoaded(p);
                    if(!loaded || (frame(x,y,z)?world.getBlockState(p).getBlock()!=matter:!world.isAirBlock(p))){
                        if(errors++==0)first=(loaded?(frame(x,y,z)?world.getBlockState(p).getBlock() instanceof BlockConstructorCore?"Use a Matter Block, not another Core":"Matter Block required":"Interior must be air"):"Chunk not loaded")+" at "+p.getX()+", "+p.getY()+", "+p.getZ();
                    }
                }
                if(errors<best){best=errors;assemblyProblem=first+" ("+errors+" mismatched cells in closest frame).";}
                if(errors==0 && selected==null){selected=direction;selectedX=cornerX;selectedZ=cornerZ;}
            }
        }
        if(selected==null)return false;
        changing=true;
        try{
            coreX=selectedX;coreZ=selectedZ;
            world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockVandorDirectional.FACING,selected),2);
            Block part=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:constructor_part"));
            for(int y=0;y<core().height;y++)for(int x=0;x<core().width;x++)for(int z=0;z<3;z++){
                BlockPos p=cell(x,y,z);if(p.equals(pos))continue;
                world.setBlockState(p,part.getDefaultState(),2);((TileEntityConstructorPart)world.getTileEntity(p)).owner(pos,frame(x,y,z));
            }
            world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockConstructorCore.FORMED,true),3);markDirty();
            world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);return true;
        }finally{changing=false;}
    }
    public void dismantle(IBlockState state){removedState=state;try{dismantle();}finally{removedState=null;}}
    public void dismantle(){
        if(world==null || world.isRemote || changing || !formed())return;
        changing=true;
        try{
            Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));
            for(int y=0;y<core().height;y++)for(int x=0;x<core().width;x++)for(int z=0;z<3;z++){
                BlockPos p=cell(x,y,z);if(p.equals(pos) || !world.isBlockLoaded(p))continue;TileEntity part=world.getTileEntity(p);
                if(part instanceof TileEntityConstructorPart && ((TileEntityConstructorPart)part).owner().equals(pos))world.setBlockState(p,frame(x,y,z)?matter.getDefaultState():net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            }
            if(removedState==null)world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockConstructorCore.FORMED,false),3);markDirty();
        }finally{changing=false;}
    }
    @Override public AxisAlignedBB getRenderBoundingBox(){BlockPos origin=pos.subtract(pivot());boolean turn=facing().getAxis()==EnumFacing.Axis.X;return new AxisAlignedBB(origin,origin.add(turn?3:core().width,core().height,turn?core().width:3));}
    @Override public double getMaxRenderDistanceSquared(){return 65536;}
    @Override public boolean hasFastRenderer(){return true;}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setInteger("ConstructorCoreX",coreX);tag.setInteger("ConstructorCoreZ",coreZ);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);coreX=Math.max(0,Math.min(4,tag.getInteger("ConstructorCoreX")));coreZ=tag.getInteger("ConstructorCoreZ")==2?2:0;}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
