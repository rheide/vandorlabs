package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

/** The inherited housing finish is the off finish. */
public final class TileEntityProgrammableTrigger extends TileEntityAnimatedScreenSelector {
    private com.vandorlabs.persistence.TriggerState trigger=com.vandorlabs.persistence.TriggerState.defaults();
    public int getLowTexture(){return trigger.low;}
    public int getMediumTexture(){return trigger.medium;}
    public boolean isLevelStates(){return trigger.bands;}
    public int getExactLevel(){return trigger.exact;}
    public void configureLevels(boolean states,int exact,int low,int medium){
        if(!com.vandorlabs.persistence.TriggerState.validExact(exact) || !ScreenHousingTextures.validChoice(low) || !ScreenHousingTextures.validChoice(medium))return;
        trigger=new com.vandorlabs.persistence.TriggerState(trigger.on,states,exact,low,medium);markDirty();
        if(world!=null)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
    }


    public int getOnTexture() { return trigger.on; }
    public int getVisibleTexture() {
        return trigger.visible(getHousingTexture(),getSignalLevel());
    }

    public void configure(int off, int on, int channel) {
        if(channel<0)return;
        configure(off,on,com.vandorlabs.redstone.ChannelList.of(channel));
    }
    public void configure(int off,int on,com.vandorlabs.redstone.ChannelList channels) {
        if (off < 0 || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(off)
                || on < 0 || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(on)
                || channels == null) return;
        setHousingTexture(off);
        trigger=new com.vandorlabs.persistence.TriggerState(on,trigger.bands,trigger.exact,trigger.low,trigger.medium);
        setRedstoneChannels(channels);
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        trigger.write(new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        trigger=com.vandorlabs.persistence.TriggerState.read(new com.vandorlabs.persistence.NbtPrimitiveData(tag),ScreenHousingTextures::clamp);
    }
}
