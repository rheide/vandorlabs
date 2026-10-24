package com.vandorlabs.tiles;

import com.vandorlabs.blocks.*;
import com.vandorlabs.navigation.*;
import com.vandorlabs.redstone.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Cached assembly geometry and an input-only, optionally secured display. */
public final class TileEntityNavigationTable extends TileEntitySecuredDisplay {
    private static final class LayoutState {
        final int connections,width,depth;final BlockPos offset,origin;final boolean formed;final EnumFacing facing;
        LayoutState(int connections,BlockPos offset,boolean formed,BlockPos origin,int width,int depth,EnumFacing facing){this.connections=connections;this.offset=offset;this.formed=formed;this.origin=origin;this.width=width;this.depth=depth;this.facing=facing;}
    }
    private volatile LayoutState layout=new LayoutState(0,BlockPos.ORIGIN,false,BlockPos.ORIGIN,1,1,EnumFacing.NORTH);
    @Override public boolean isItemValidForSlot(int slot,net.minecraft.item.ItemStack stack){return hasMount()&&super.isItemValidForSlot(slot,stack);}
    @Override protected TileEntitySecuredDisplay displayOwner(){return reader();}
    public net.minecraft.block.state.IBlockState renderState(net.minecraft.block.state.IBlockState state){LayoutState current=layout;return state.withProperty(BlockNavigationTable.CONNECTIONS,current.connections).withProperty(BlockNavigationTable.CHIP,current.formed&&current.offset.equals(BlockPos.ORIGIN)&&screenSettings().security);}
    public int connections(){return layout.connections;}
    public boolean ownsReader(){LayoutState current=layout;return current.formed&&current.offset.equals(BlockPos.ORIGIN);}
    public boolean hasMount(){return ownsReader()&&screenSettings().security;}
    public boolean formed(){return layout.formed;}
    public BlockPos owner(){return pos.add(layout.offset);}
    public BlockPos origin(){return pos.add(layout.origin);}
    public int assemblyWidth(){return layout.width;}public int assemblyDepth(){return layout.depth;}
    public EnumFacing displayFacing(){return layout.facing;}
    public TileEntityNavigationTable reader(){
        if(!layout.formed)return this;
        net.minecraft.tileentity.TileEntity tile=world!=null&&world.isBlockLoaded(owner())?world.getTileEntity(owner()):null;
        return tile instanceof TileEntityNavigationTable?(TileEntityNavigationTable)tile:null;
    }
    public void layout(int mask,BlockPos owner,boolean valid){layout(mask,owner,valid,pos,1,1,world.getBlockState(pos).getValue(BlockVandorDirectional.FACING));}
    public void layout(int mask,BlockPos owner,boolean valid,BlockPos origin,int width,int depth,EnumFacing facing){
        BlockPos offset=owner.subtract(pos),relativeOrigin=origin.subtract(pos);LayoutState before=layout;
        if(before.connections==mask&&before.offset.equals(offset)&&before.formed==valid&&before.origin.equals(relativeOrigin)&&before.width==width&&before.depth==depth&&before.facing==facing)return;
        layout=new LayoutState(mask,offset,valid,relativeOrigin,width,depth,facing);publish();refresh();
    }
    @Override protected List<TileEntityNavigationTable> members(){
        List<TileEntityNavigationTable> result=new ArrayList<>();LayoutState current=layout;
        if(!current.formed){result.add(this);return result;}
        NavigationLayout.Facing direction=NavigationLayout.Facing.valueOf(current.facing.name());BlockPos origin=origin();
        NavigationLayout.Cell anchor=new NavigationLayout.Cell(origin.getX(),origin.getY(),origin.getZ());
        for(int x=0;x<current.width;x++)for(int z=0;z<current.depth;z++){
            NavigationLayout.Vec3 p=direction.modulePoint(anchor,new NavigationLayout.Vec3(x+.5,0,z+.5));BlockPos at=new BlockPos(p.x,p.y,p.z);
            if(!world.isBlockLoaded(at))continue;net.minecraft.tileentity.TileEntity tile=world.getTileEntity(at);
            if(tile instanceof TileEntityNavigationTable&&((TileEntityNavigationTable)tile).owner().equals(owner()))result.add((TileEntityNavigationTable)tile);
        }
        return result;
    }
    public boolean nearDisplayMember(net.minecraft.entity.player.EntityPlayer player){
        if(player.world!=world||!formed())return false;
        for(TileEntityNavigationTable tile:members())if(player.getDistanceSq(tile.pos)<=64)return true;
        return false;
    }
    public MachineMountSurface mount(){
        if(!hasMount())return null;EnumFacing facing=world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);
        if((connections()&NavigationLayout.NORTH)!=0)facing=displayFacing();
        BlockNavigationTable block=(BlockNavigationTable)getBlockType();double height=block.kind==NavigationLayout.Kind.FLAT?14D/16:8D/16;
        Vec3d point=MachineMountSurface.rotate(new Vec3d(.5,height-TokenSlotGeometry.CHIP_SIZE/2-.5/16,-.02625),facing,1,1).add(new Vec3d(pos));
        return new MachineMountSurface(point,facing,TokenSlotGeometry.CHIP_SIZE,TokenSlotGeometry.CHIP_SIZE);
    }
    @Override public boolean isUsableByPlayer(net.minecraft.entity.player.EntityPlayer player){return super.isUsableByPlayer(player);}
    @Override public void onLoad(){super.onLoad();DeferredTileLoad.schedule(this,()->NavigationAssemblies.around(world,pos));}
    @Override public void onChunkUnload(){NavigationAssemblies.around(world,pos);super.onChunkUnload();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);LayoutState current=layout;tag.setInteger("NavigationConnections",current.connections);tag.setLong("NavigationOwner",current.offset.toLong());tag.setBoolean("NavigationFormed",current.formed);
        tag.setLong("NavigationOrigin",current.origin.toLong());tag.setInteger("NavigationWidth",current.width);tag.setInteger("NavigationDepth",current.depth);tag.setInteger("NavigationFacing",current.facing.getHorizontalIndex());return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);
        int width=Math.max(1,Math.min(256,tag.getInteger("NavigationWidth"))),depth=Math.max(1,Math.min(256,tag.getInteger("NavigationDepth")));if(width*depth>256){width=1;depth=1;}
        layout=new LayoutState(tag.getInteger("NavigationConnections")&15,BlockPos.fromLong(tag.getLong("NavigationOwner")),tag.getBoolean("NavigationFormed"),BlockPos.fromLong(tag.getLong("NavigationOrigin")),width,depth,EnumFacing.getHorizontal(tag.getInteger("NavigationFacing")));
    }
}
