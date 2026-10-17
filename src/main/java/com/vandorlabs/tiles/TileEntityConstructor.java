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
public final class TileEntityConstructor extends TileEntity implements ITickable {
    private boolean changing;private int ticks;private IBlockState removedState;
    public BlockConstructorCore core(){return (BlockConstructorCore)(removedState==null?getBlockType():removedState.getBlock());}
    public EnumFacing facing(){return (removedState==null?world.getBlockState(pos):removedState).getValue(BlockVandorDirectional.FACING);}
    public boolean formed(){return removedState!=null?removedState.getValue(BlockConstructorCore.FORMED):world!=null && world.getBlockState(pos).getBlock() instanceof BlockConstructorCore && world.getBlockState(pos).getValue(BlockConstructorCore.FORMED);}
    public BlockPos pivot(){switch(facing()){case EAST:return new BlockPos(2,0,0);case SOUTH:return new BlockPos(core().width-1,0,2);case WEST:return new BlockPos(0,0,core().width-1);default:return BlockPos.ORIGIN;}}
    public BlockPos cell(int x,int y,int z){BlockPos p;switch(facing()){case EAST:p=new BlockPos(2-z,y,x);break;case SOUTH:p=new BlockPos(core().width-1-x,y,2-z);break;case WEST:p=new BlockPos(z,y,core().width-1-x);break;default:p=new BlockPos(x,y,z);}return pos.add(p.subtract(pivot()));}
    public boolean frame(int x,int y,int z){return y==0 || (x==0 || x==core().width-1) && (z==0 || z==2) || y==core().height-1 && (x==0 || x==core().width-1 || z==0 || z==2);}
    public boolean assemble(){
        if(world==null || world.isRemote || changing || formed())return false;
        Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));
        for(int y=0;y<core().height;y++)for(int x=0;x<core().width;x++)for(int z=0;z<3;z++){
            BlockPos p=cell(x,y,z);if(p.equals(pos))continue;
            if(!world.isBlockLoaded(p) || (frame(x,y,z)?world.getBlockState(p).getBlock()!=matter:!world.isAirBlock(p)))return false;
        }
        changing=true;
        try{
            Block part=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:constructor_part"));
            for(int y=0;y<core().height;y++)for(int x=0;x<core().width;x++)for(int z=0;z<3;z++){
                BlockPos p=cell(x,y,z);if(p.equals(pos))continue;
                world.setBlockState(p,part.getDefaultState(),2);((TileEntityConstructorPart)world.getTileEntity(p)).owner(pos,frame(x,y,z));
            }
            world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockConstructorCore.FORMED,true),3);markDirty();return true;
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
    @Override public void update(){if(world.isRemote || ++ticks%20!=0 || changing)return;if(!formed())assemble();}
    @Override public AxisAlignedBB getRenderBoundingBox(){BlockPos origin=pos.subtract(pivot());boolean turn=facing().getAxis()==EnumFacing.Axis.X;return new AxisAlignedBB(origin,origin.add(turn?3:core().width,core().height,turn?core().width:3));}
    @Override public double getMaxRenderDistanceSquared(){return 65536;}
    @Override public boolean hasFastRenderer(){return true;}
}
