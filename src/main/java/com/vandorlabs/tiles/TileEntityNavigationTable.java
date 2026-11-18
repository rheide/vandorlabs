package com.vandorlabs.tiles;

import com.vandorlabs.blocks.*;
import com.vandorlabs.navigation.*;
import com.vandorlabs.redstone.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Cached assembly geometry and an input-only, optionally secured display. */
public final class TileEntityNavigationTable extends TileEntitySecuredDisplay implements net.minecraft.util.ITickable {
    @Override public void update(){
        if(world==null||world.isRemote||navigationModule.isEmpty()||!formed()||(world.getTotalWorldTime()+pos.toLong())%20!=0||!authoritative()||com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)||com.vandorlabs.gear.ArmTransactions.locked(world,pos))return;
        TileEntityNavigationTable table=reader();if(table!=null&&table.screenSettings().texture==NavigationMap.SURFACE)NavigationService.recordSources(table);
    }
    private static final class LayoutState {
        NavigationLayout.Screen plane;AxisAlignedBB bounds;
        final int connections,width,depth;final BlockPos offset,origin;final boolean formed;final EnumFacing facing;
        LayoutState(int connections,BlockPos offset,boolean formed,BlockPos origin,int width,int depth,EnumFacing facing){this.connections=connections;this.offset=offset;this.formed=formed;this.origin=origin;this.width=width;this.depth=depth;this.facing=facing;}
    }
    private net.minecraft.item.ItemStack navigationModule=net.minecraft.item.ItemStack.EMPTY;
    private BlockPos moduleStorageOffset=BlockPos.ORIGIN;private boolean moduleBay;private EnumFacing moduleFacing=EnumFacing.NORTH;
    private long moduleSequence;
    public net.minecraft.item.ItemStack storedModule(){return navigationModule;}
    public long moduleSequence(){TileEntityNavigationTable owner=moduleStorage();return owner==null?0:owner.moduleSequence;}
    public boolean hasModuleBay(){return formed()&&moduleBay;}
    public EnumFacing moduleFacing(){return moduleFacing;}
    public TileEntityNavigationTable moduleStorage(){BlockPos at=pos.add(moduleStorageOffset);net.minecraft.tileentity.TileEntity raw=world!=null&&world.isBlockLoaded(at,false)?world.getTileEntity(at):null;return raw instanceof TileEntityNavigationTable?(TileEntityNavigationTable)raw:null;}
    public net.minecraft.item.ItemStack moduleStack(){TileEntityNavigationTable owner=moduleStorage();return owner==null?net.minecraft.item.ItemStack.EMPTY:owner.navigationModule;}
    public void moduleRole(BlockPos storage,boolean bay,EnumFacing facing){BlockPos offset=storage.subtract(pos);if(moduleStorageOffset.equals(offset)&&moduleBay==bay&&moduleFacing==facing)return;moduleStorageOffset=offset;moduleBay=bay;moduleFacing=facing;publish();}
    public void installModule(net.minecraft.item.ItemStack stack){if(!authoritative()||!navigationModule.isEmpty()||!NavigationModuleData.item(stack))return;navigationModule=stack.copy();navigationModule.setCount(1);moduleSequence++;publish();NavigationService.moduleChanged(reader());}
    public net.minecraft.item.ItemStack takeModule(){if(!authoritative())return net.minecraft.item.ItemStack.EMPTY;net.minecraft.item.ItemStack result=navigationModule;if(!result.isEmpty()){navigationModule=net.minecraft.item.ItemStack.EMPTY;moduleSequence++;publish();NavigationService.moduleChanged(reader());}return result;}
    public void ejectModule(){if(world==null||world.isRemote||com.vandorlabs.vehicle.VehicleTransfer.isMutating(world))return;net.minecraft.item.ItemStack stack=takeModule();if(!stack.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5,pos.getY()+.25,pos.getZ()+.5,stack);}
    public void moduleDataChanged(){markDirty();/* Coverage payload stays server-side until the item is removed. */}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound tag=super.getUpdateTag();if(!navigationModule.isEmpty())tag.setTag("NavigationDataModule",NavigationModuleData.summary(navigationModule).writeToNBT(new NBTTagCompound()));return tag;}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    // Absence means an explicit legacy selection; do not add a field to old ownership receipts.
    private boolean navigationExplicit,navigationExplicitPresent=true;
    public boolean navigationExplicit(){return navigationExplicit;}
    public void navigationExplicitSelection(){for(TileEntityNavigationTable tile:members()){tile.navigationExplicit=true;tile.markDirty();}}
    @Override public boolean moveReaderTo(TileEntityTokenSlot target){if(!super.moveReaderTo(target))return false;if(target instanceof TileEntityNavigationTable){((TileEntityNavigationTable)target).navigationExplicit=navigationExplicit;NavigationService.moveView(this,target);}return true;}
    public TileEntityNavigationTable(){settings=new NavigationScreenSettings(NavigationMap.SURFACE,true,1,false,false);}
    private volatile LayoutState layout=new LayoutState(0,BlockPos.ORIGIN,false,BlockPos.ORIGIN,1,1,EnumFacing.NORTH);
    @Override protected boolean displayBlock(net.minecraft.block.Block block){return block instanceof BlockNavigationTable;}
    @Override public boolean isItemValidForSlot(int slot,net.minecraft.item.ItemStack stack){return hasMount()&&super.isItemValidForSlot(slot,stack);}
    @Override protected TileEntitySecuredDisplay displayOwner(){return reader();}
    public net.minecraft.block.state.IBlockState renderState(net.minecraft.block.state.IBlockState state){LayoutState current=layout;return state.withProperty(BlockNavigationTable.CONNECTIONS,current.connections).withProperty(BlockNavigationTable.CHIP,current.formed&&current.offset.equals(BlockPos.ORIGIN));}
    public int connections(){return layout.connections;}
    public boolean ownsReader(){LayoutState current=layout;return current.formed&&current.offset.equals(BlockPos.ORIGIN);}
    public boolean hasMount(){return ownsReader();}
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
    public NavigationLayout.Screen navigationPlane(){
        LayoutState s=layout;if(s.plane==null){BlockNavigationTable block=(BlockNavigationTable)getBlockType();BlockPos p=origin();s.plane=NavigationLayout.mapScreen(block.kind,NavigationLayout.Facing.valueOf(s.facing.name()),new NavigationLayout.Cell(p.getX(),p.getY(),p.getZ()),s.width,s.depth);}return s.plane;
    }
    public AxisAlignedBB navigationBounds(){
        // OptiFine retains tiles across chunk updates and may query after native capture/removal.
        if(!renderReady())return new AxisAlignedBB(pos);
        LayoutState state=layout;if(state.bounds==null){NavigationLayout.Screen s=navigationPlane();double minX=Math.min(Math.min(s.topLeft.x,s.topRight.x),Math.min(s.bottomLeft.x,s.bottomRight.x)),maxX=Math.max(Math.max(s.topLeft.x,s.topRight.x),Math.max(s.bottomLeft.x,s.bottomRight.x));double minZ=Math.min(Math.min(s.topLeft.z,s.topRight.z),Math.min(s.bottomLeft.z,s.bottomRight.z)),maxZ=Math.max(Math.max(s.topLeft.z,s.topRight.z),Math.max(s.bottomLeft.z,s.bottomRight.z));state.bounds=new AxisAlignedBB(minX-.5,pos.getY()-.5,minZ-.5,maxX+.5,pos.getY()+1.5,maxZ+.5);}return state.bounds;
    }
    @Override public double getMaxRenderDistanceSquared(){double reach=64+layout.width+layout.depth;return ownsReader()&&screenSettings().texture==NavigationMap.SURFACE?reach*reach:super.getMaxRenderDistanceSquared();}
    @Override public AxisAlignedBB getRenderBoundingBox(){return formed()&&ownsReader()&&screenSettings().texture==NavigationMap.SURFACE?navigationBounds():super.getRenderBoundingBox();}
    public boolean nearDisplayMember(net.minecraft.entity.player.EntityPlayer player){
        if(player.world!=world||!formed())return false;AxisAlignedBB b=navigationBounds();double x=Math.max(b.minX,Math.min(b.maxX,player.posX)),y=Math.max(b.minY,Math.min(b.maxY,player.posY)),z=Math.max(b.minZ,Math.min(b.maxZ,player.posZ));return player.getDistanceSq(x,y,z)<=49;
    }
    public MachineMountSurface mount(){
        if(!renderReady()||!hasMount())return null;EnumFacing facing=world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);
        if((connections()&NavigationLayout.NORTH)!=0)facing=displayFacing();
        BlockNavigationTable block=(BlockNavigationTable)getBlockType();double height=block.kind==NavigationLayout.Kind.FLAT?14D/16:8D/16;
        Vec3d point=MachineMountSurface.rotate(new Vec3d(.5,height-TokenSlotGeometry.CHIP_SIZE/2-.5/16,-.02625),facing,1,1).add(new Vec3d(pos));
        return new MachineMountSurface(point,facing,TokenSlotGeometry.CHIP_SIZE,TokenSlotGeometry.CHIP_SIZE);
    }
    @Override public boolean isUsableByPlayer(net.minecraft.entity.player.EntityPlayer player){return super.isUsableByPlayer(player);}
    @Override public void onLoad(){super.onLoad();DeferredTileLoad.schedule(this,()->NavigationAssemblies.around(world,pos));}
    @Override public void onChunkUnload(){NavigationAssemblies.around(world,pos);super.onChunkUnload();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);if(!navigationModule.isEmpty())tag.setTag("NavigationDataModule",navigationModule.writeToNBT(new NBTTagCompound()));else tag.removeTag("NavigationDataModule");if(moduleBay||!moduleStorageOffset.equals(BlockPos.ORIGIN)||moduleSequence!=0||!navigationModule.isEmpty()){tag.setLong("NavigationModuleSequence",moduleSequence);tag.setLong("NavigationModuleStorage",moduleStorageOffset.toLong());tag.setBoolean("NavigationModuleBay",moduleBay);tag.setInteger("NavigationModuleFacing",moduleFacing.getHorizontalIndex());}else{for(String key:new String[]{"NavigationModuleSequence","NavigationModuleStorage","NavigationModuleBay","NavigationModuleFacing"})tag.removeTag(key);}if(navigationExplicitPresent||!navigationExplicit)tag.setBoolean("NavigationExplicit",navigationExplicit);else tag.removeTag("NavigationExplicit");LayoutState current=layout;tag.setInteger("NavigationConnections",current.connections);tag.setLong("NavigationOwner",current.offset.toLong());tag.setBoolean("NavigationFormed",current.formed);
        tag.setLong("NavigationOrigin",current.origin.toLong());tag.setInteger("NavigationWidth",current.width);tag.setInteger("NavigationDepth",current.depth);tag.setInteger("NavigationFacing",current.facing.getHorizontalIndex());return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);navigationModule=tag.hasKey("NavigationDataModule",10)?new net.minecraft.item.ItemStack(tag.getCompoundTag("NavigationDataModule")):net.minecraft.item.ItemStack.EMPTY;if(!navigationModule.isEmpty())navigationModule.setCount(1);moduleSequence=Math.max(0,tag.getLong("NavigationModuleSequence"));moduleStorageOffset=BlockPos.fromLong(tag.getLong("NavigationModuleStorage"));if(Math.abs((long)moduleStorageOffset.getX())>256||Math.abs((long)moduleStorageOffset.getY())>0||Math.abs((long)moduleStorageOffset.getZ())>256)moduleStorageOffset=BlockPos.ORIGIN;moduleBay=tag.getBoolean("NavigationModuleBay");moduleFacing=EnumFacing.getHorizontal(tag.getInteger("NavigationModuleFacing"));navigationExplicitPresent=tag.hasKey("NavigationExplicit");navigationExplicit=!tag.hasKey("NavigationExplicit",1)||tag.getBoolean("NavigationExplicit");
        int width=Math.max(1,Math.min(256,tag.getInteger("NavigationWidth"))),depth=Math.max(1,Math.min(256,tag.getInteger("NavigationDepth")));if(width*depth>256){width=1;depth=1;}
        layout=new LayoutState(tag.getInteger("NavigationConnections")&15,BlockPos.fromLong(tag.getLong("NavigationOwner")),tag.getBoolean("NavigationFormed"),BlockPos.fromLong(tag.getLong("NavigationOrigin")),width,depth,EnumFacing.getHorizontal(tag.getInteger("NavigationFacing")));
    }
}
