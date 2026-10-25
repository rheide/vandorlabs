package com.vandorlabs.tiles;

import com.vandorlabs.navigation.*;
import com.vandorlabs.redstone.*;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

/** Shared input-only activation, credentials and persistence for modeled displays. */
public abstract class TileEntitySecuredDisplay extends TileEntityTokenSlot {
    protected volatile NavigationScreenSettings settings=NavigationScreenSettings.DEFAULT;
    private boolean channelInput,screenOn,configuring;
    protected boolean physicalInput;
    protected TileEntitySecuredDisplay(){super(true);}
    @Override public boolean chip(){return true;}
    public abstract boolean formed();
    protected abstract boolean displayBlock(net.minecraft.block.Block block);
    public final boolean renderReady(){return world!=null&&!isInvalid()&&world.isBlockLoaded(pos)&&displayBlock(world.getBlockState(pos).getBlock())&&world.getTileEntity(pos)==this&&formed();}
    protected TileEntitySecuredDisplay displayOwner(){return this;}
    protected List<? extends TileEntitySecuredDisplay> members(){return Collections.singletonList(this);}
    public boolean supportsSecurity(){return false;}
    @Override public void configure(boolean any,int allowed,int behavior){super.configure(true,127,0);}
    @Override public boolean authorized(net.minecraft.item.ItemStack stack){return accepts(stack);}
    @Override public int effectiveAllowed(){return 127;}
    protected boolean inputPowered(boolean aggregate){return aggregate;}
    @Override public boolean isItemValidForSlot(int slot,net.minecraft.item.ItemStack stack){return super.isItemValidForSlot(slot,stack);}
    public int surfaceCount(){return 1;}
    public String surfaceLabel(int slot){return "Screen";}
    public int surfaceChoice(int slot){return screenSettings().texture;}
    public boolean nativeArtwork(){return false;}
    protected final boolean authoritative(){return world!=null&&!(world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).parent:world).isRemote;}
    public final NavigationScreenSettings screenSettings(){return settings;}
    public final boolean screenOn(){return screenOn;}
    public final boolean credentialPresent(){return !isEmpty()&&matches();}
    @Override public boolean indicatorOn(){return credentialPresent();}
    public void configureDisplay(NavigationScreenSettings next,boolean any,int allowed,int behavior,ChannelList channels){
        if(!authoritative())return;TileEntitySecuredDisplay root=displayOwner();if(root==null)return;
        next=new NavigationScreenSettings(next.texture,next.animated,next.speed,next.redstone,false);
        root.configuring=true;
        try{
            root.settings=next;root.configure(true,127,0);root.setRedstoneChannels(channels);
            for(TileEntitySecuredDisplay tile:root.members()){
                tile.settings=next;
                if(tile!=root){tile.configure(root.any,root.allowed,root.behavior);tile.setRedstoneChannels(channels);}
                tile.publish();
            }
        }finally{root.configuring=false;}
        root.refreshInputs();
    }
    @Override public boolean moveReaderTo(TileEntityTokenSlot target){
        NavigationScreenSettings saved=settings;if(!super.moveReaderTo(target))return false;
        if(target instanceof TileEntitySecuredDisplay){((TileEntitySecuredDisplay)target).settings=saved;((TileEntitySecuredDisplay)target).publish();}return true;
    }
    protected boolean physicalPower(){return LoadedRedstonePower.isPowered(world,pos);}
    protected final void physicalInput(boolean value){if(physicalInput!=value){physicalInput=value;refresh();}}
    public void sampleInput(){if(authoritative())physicalInput(physicalPower());}
    public void refreshInputs(){if(!authoritative())return;for(TileEntitySecuredDisplay tile:members())tile.physicalInput=tile.physicalPower();refresh();}
    @Override public final void setChannelSignal(boolean value){if(channelInput!=value){channelInput=value;refresh();}}
    @Override public final int getOutputLevel(){return 0;}
    @Override public final boolean hasLocalRedstoneSignal(){return false;}
    @Override public final boolean hasLocalRedstoneSignal(int channel){return false;}
    @Override public final int localSignalLevel(int channel){return 0;}
    @Override public void refresh(){
        if(!authoritative())return;TileEntitySecuredDisplay root=displayOwner();if(root==null||root.configuring)return;
        if(SignalUpdateBatch.isActive()){SignalUpdateBatch.afterSignals(root,root::refresh);return;}
        List<? extends TileEntitySecuredDisplay> members=root.members();boolean powered=false;
        for(TileEntitySecuredDisplay tile:members)if(tile.physicalInput||tile.channelInput){powered=true;break;}
        NavigationScreenSettings config=root.settings;
        boolean active=root.formed()&&NavigationScreenPolicy.visible(config.redstone,false,root.inputPowered(powered),false);
        for(TileEntitySecuredDisplay tile:members)if(tile.screenOn!=active){tile.screenOn=active;tile.publish();}
    }
    protected final void publish(){markDirty();if(world!=null&&!world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);settings.write(tag);tag.setBoolean("NavigationScreenOn",screenOn);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);NavigationScreenSettings saved=NavigationScreenSettings.read(tag);settings=new NavigationScreenSettings(saved.texture,saved.animated,saved.speed,saved.redstone,false);any=true;allowed=127;behavior=0;screenOn=tag.getBoolean("NavigationScreenOn");}
}
