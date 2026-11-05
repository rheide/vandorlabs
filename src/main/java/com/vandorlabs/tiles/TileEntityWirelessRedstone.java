package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockWirelessRedstone;
import com.vandorlabs.redstone.*;
import com.vandorlabs.persistence.WirelessRedstoneData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Event-driven directional endpoints of the existing dimension-wide channel bank. */
public final class TileEntityWirelessRedstone extends TileEntityRedstoneChannel {
    private boolean analog=true;
    private int onLevel=15,offLevel,inputLevel,receivedLevel;
    public boolean transmitter(){return world!=null&&world.getBlockState(pos).getBlock() instanceof BlockWirelessRedstone&&((BlockWirelessRedstone)world.getBlockState(pos).getBlock()).transmitter;}
    public boolean analog(){return analog;}
    public int onLevel(){return onLevel;}
    public int offLevel(){return offLevel;}
    public EnumFacing port(){return world.getBlockState(pos).getValue(BlockWirelessRedstone.FACING);}
    @Override public NBTTagCompound configuration(){NBTTagCompound tag=super.configuration();tag.setBoolean(WirelessRedstoneData.ANALOG,analog);tag.setInteger(WirelessRedstoneData.ON,onLevel);tag.setInteger(WirelessRedstoneData.OFF,offLevel);return tag;}
    @Override public boolean isChannelLatch(){return false;}
    @Override public boolean hasLocalRedstoneSignal(){return transmitter()&&inputLevel>0;}
    @Override public boolean hasLocalRedstoneSignal(int channel){return hasLocalRedstoneSignal();}
    @Override public int localSignalLevel(int channel){return transmitter()?WirelessRedstoneData.sourceLevel(analog,inputLevel):0;}
    @Override public int getOutputLevel(){return transmitter()?0:WirelessRedstoneData.outputLevel(analog,receivedLevel,onLevel,offLevel);}
    @Override public void setLocalOn(boolean ignored){}
    @Override public void setChannelSignal(boolean value){setChannelLevel(value?15:0);}
    @Override public void setChannelLevel(int value){int before=getOutputLevel();receivedLevel=WirelessRedstoneData.level(value);if(before!=getOutputLevel())changed(true);}
    public void configure(boolean analog,int on,int off){int before=getOutputLevel();this.analog=analog;onLevel=WirelessRedstoneData.level(on);offLevel=WirelessRedstoneData.level(off);if(transmitter()){sampleInput();RedstoneChannels.inputChanged(this);}changed(before!=getOutputLevel());}
    public void sampleInput(){
        if(world==null||world.isRemote||!transmitter())return;
        EnumFacing side=port();BlockPos source=pos.offset(side);int level=0;
        if(world.isBlockLoaded(source)){
            IBlockState state=world.getBlockState(source);
            if(state.getBlock().shouldCheckWeakPower(state,world,source,side)){
                for(EnumFacing direction:EnumFacing.values()){BlockPos neighbor=source.offset(direction);if(world.isBlockLoaded(neighbor))level=Math.max(level,world.getBlockState(neighbor).getStrongPower(world,neighbor,direction));}
            }else level=state.getWeakPower(world,source,side);
        }
        level=WirelessRedstoneData.level(level);if(level==inputLevel)return;inputLevel=level;changed(true);RedstoneChannels.inputChanged(this);
    }
    private void changed(boolean output){
        markDirty();if(world==null||world.isRemote)return;IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);
        if(output){Runnable notify=()->{if(world.getTileEntity(pos)!=this)return;world.notifyNeighborsOfStateChange(pos,state.getBlock(),false);world.notifyNeighborsOfStateChange(pos.offset(port()),state.getBlock(),false);world.notifyNeighborsOfStateChange(pos.offset(port().getOpposite()),state.getBlock(),false);};if(SignalUpdateBatch.isActive())SignalUpdateBatch.afterSignals(this,notify);else notify.run();}
    }
    public void rotatePort(EnumFacing facing){if(world==null)return;world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockWirelessRedstone.FACING,facing),3);sampleInput();changed(true);}
    @Override public void onLoad(){super.onLoad();DeferredTileLoad.schedule(this,()->{sampleInput();changed(true);});}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean(WirelessRedstoneData.ANALOG,analog);tag.setInteger(WirelessRedstoneData.ON,onLevel);tag.setInteger(WirelessRedstoneData.OFF,offLevel);tag.setInteger(WirelessRedstoneData.INPUT,inputLevel);tag.setInteger(WirelessRedstoneData.RECEIVED,receivedLevel);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);analog=WirelessRedstoneData.savedAnalog(tag.hasKey(WirelessRedstoneData.ANALOG),tag.getBoolean(WirelessRedstoneData.ANALOG));onLevel=WirelessRedstoneData.savedOn(tag.hasKey(WirelessRedstoneData.ON,3),tag.getInteger(WirelessRedstoneData.ON));offLevel=WirelessRedstoneData.level(tag.getInteger(WirelessRedstoneData.OFF));inputLevel=WirelessRedstoneData.level(tag.getInteger(WirelessRedstoneData.INPUT));receivedLevel=WirelessRedstoneData.level(tag.getInteger(WirelessRedstoneData.RECEIVED));}
}
