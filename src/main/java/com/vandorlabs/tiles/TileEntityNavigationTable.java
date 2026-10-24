package com.vandorlabs.tiles;

import com.vandorlabs.blocks.*;
import com.vandorlabs.navigation.NavigationAssemblies;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;

/** Cached layout state; rendering never traverses the joined component. */
public final class TileEntityNavigationTable extends TileEntityTokenSlot {
    private static final class LayoutState {
        final int connections;final BlockPos offset;final boolean formed;
        LayoutState(int connections,BlockPos offset,boolean formed){this.connections=connections;this.offset=offset;this.formed=formed;}
    }
    private volatile LayoutState layout=new LayoutState(0,BlockPos.ORIGIN,false);
    public TileEntityNavigationTable(){super(true);}
    @Override public boolean chip(){return true;}
    @Override public boolean isItemValidForSlot(int slot,net.minecraft.item.ItemStack stack){return hasMount()&&super.isItemValidForSlot(slot,stack);}
    public net.minecraft.block.state.IBlockState renderState(net.minecraft.block.state.IBlockState state){LayoutState current=layout;return state.withProperty(BlockNavigationTable.CONNECTIONS,current.connections).withProperty(BlockNavigationTable.CHIP,current.formed&&current.offset.equals(BlockPos.ORIGIN));}
    public int connections(){return layout.connections;}
    public boolean hasMount(){LayoutState current=layout;return current.formed&&current.offset.equals(BlockPos.ORIGIN);}
    public BlockPos owner(){return pos.add(layout.offset);}
    public TileEntityNavigationTable reader(){if(!layout.formed)return isEmpty()?null:this;net.minecraft.tileentity.TileEntity tile=world.isBlockLoaded(owner())?world.getTileEntity(owner()):null;return tile instanceof TileEntityNavigationTable?(TileEntityNavigationTable)tile:null;}
    public void layout(int mask,BlockPos owner,boolean valid){
        BlockPos offset=owner.subtract(pos);LayoutState before=layout;if(before.connections==mask&&before.offset.equals(offset)&&before.formed==valid)return;
        layout=new LayoutState(mask,offset,valid);markDirty();refresh();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);
    }
    @Override public int getOutputLevel(){return hasMount()?super.getOutputLevel():0;}
    public MachineMountSurface mount(){
        if(!hasMount())return null;net.minecraft.util.EnumFacing facing=world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);
        Vec3d point=MachineMountSurface.rotate(new Vec3d(.5,.1875,.06125),facing,1,1).add(new Vec3d(pos));
        return new MachineMountSurface(point,facing,6D/16,4D/16);
    }
    @Override public boolean isUsableByPlayer(net.minecraft.entity.player.EntityPlayer player){return (hasMount()||!isEmpty())&&super.isUsableByPlayer(player);}
    @Override public void onLoad(){super.onLoad();DeferredTileLoad.schedule(this,()->NavigationAssemblies.around(world,pos));}
    @Override public void onChunkUnload(){NavigationAssemblies.around(world,pos);super.onChunkUnload();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);LayoutState current=layout;tag.setInteger("NavigationConnections",current.connections);tag.setLong("NavigationOwner",current.offset.toLong());tag.setBoolean("NavigationFormed",current.formed);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);layout=new LayoutState(tag.getInteger("NavigationConnections")&15,BlockPos.fromLong(tag.getLong("NavigationOwner")),tag.getBoolean("NavigationFormed"));}
}
